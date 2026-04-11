package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.BranchService
import com.ai.challenge.core.chat.ChatService
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.chat.model.BranchName
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.UsageService
import com.ai.challenge.core.usage.model.Cost
import com.ai.challenge.core.usage.model.TokenCount
import com.ai.challenge.core.usage.model.UsageRecord
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch
import java.math.BigDecimal

class ChatStoreFactory(
    private val storeFactory: StoreFactory,
    private val chatService: ChatService,
    private val sessionService: SessionService,
    private val usageService: UsageService,
    private val branchService: BranchService,
) {
    fun create(): ChatStore =
        object : ChatStore,
            Store<ChatStore.Intent, ChatStore.State, Nothing> by storeFactory.create(
                name = "ChatStore",
                initialState = ChatStore.State(),
                executorFactory = { ExecutorImpl(
                    chatService = chatService,
                    sessionService = sessionService,
                    usageService = usageService,
                    branchService = branchService,
                ) },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class SessionLoaded(
            val sessionId: AgentSessionId,
            val messages: List<UiMessage>,
            val turnUsage: Map<TurnId, UsageRecord>,
            val sessionUsage: UsageRecord,
        ) : Msg
        data class UserMessage(val text: String) : Msg
        data class AgentResponseMsg(
            val text: String,
            val turnId: TurnId,
            val usage: UsageRecord,
        ) : Msg
        data class Error(val text: String) : Msg
        data object Loading : Msg
        data object LoadingComplete : Msg
        data class BranchesLoaded(
            val branches: List<Branch>,
            val activeBranch: Branch?,
            val isBranchingEnabled: Boolean,
            val branchParentMap: Map<BranchId, BranchId?>,
        ) : Msg
        data class BranchSwitched(
            val messages: List<UiMessage>,
            val activeBranch: Branch?,
            val branches: List<Branch>,
            val turnUsage: Map<TurnId, UsageRecord>,
            val sessionUsage: UsageRecord,
            val branchParentMap: Map<BranchId, BranchId?>,
        ) : Msg
    }

    private class ExecutorImpl(
        private val chatService: ChatService,
        private val sessionService: SessionService,
        private val usageService: UsageService,
        private val branchService: BranchService,
    ) : CoroutineExecutor<ChatStore.Intent, Nothing, ChatStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: ChatStore.Intent) {
            when (intent) {
                is ChatStore.Intent.SendMessage -> handleSendMessage(text = intent.text)
                is ChatStore.Intent.LoadSession -> handleLoadSession(sessionId = intent.sessionId)
                is ChatStore.Intent.CreateBranch -> handleCreateBranch(name = intent.name, parentTurnId = intent.parentTurnId)
                is ChatStore.Intent.SwitchBranch -> handleSwitchBranch(branchId = intent.branchId)
                is ChatStore.Intent.DeleteBranch -> handleDeleteBranch(branchId = intent.branchId)
                is ChatStore.Intent.LoadBranches -> handleLoadBranches()
            }
        }

        private fun handleLoadSession(sessionId: AgentSessionId) {
            scope.launch {
                val history = when (val r = branchService.getActiveTurns(sessionId = sessionId)) {
                    is Either.Right -> r.value
                    is Either.Left -> emptyList()
                }
                val messages = history.flatMap { turn ->
                    listOf(
                        UiMessage(text = turn.userMessage.value, isUser = true, turnId = turn.id),
                        UiMessage(text = turn.assistantMessage.value, isUser = false, turnId = turn.id),
                    )
                }
                val turnUsage = when (val r = usageService.getBySession(sessionId = sessionId)) {
                    is Either.Right -> r.value
                    is Either.Left -> emptyMap()
                }
                val sessionUsage = turnUsage.values.fold(emptyUsageRecord()) { acc, u -> acc + u }
                dispatch(Msg.SessionLoaded(
                    sessionId = sessionId,
                    messages = messages,
                    turnUsage = turnUsage,
                    sessionUsage = sessionUsage,
                ))
                handleLoadBranches()
            }
        }

        private fun handleSendMessage(text: String) {
            val sessionId = state().sessionId ?: return
            dispatch(Msg.UserMessage(text = text))
            dispatch(Msg.Loading)

            scope.launch {
                when (val result = chatService.send(sessionId = sessionId, message = MessageContent(value = text))) {
                    is Either.Right -> {
                        val turn = result.value
                        dispatch(
                            Msg.AgentResponseMsg(
                                text = turn.assistantMessage.value,
                                turnId = turn.id,
                                usage = turn.usage,
                            )
                        )
                    }
                    is Either.Left -> dispatch(Msg.Error(text = result.value.message))
                }
                dispatch(Msg.LoadingComplete)

                when (val sessionResult = sessionService.get(id = sessionId)) {
                    is Either.Right -> {
                        if (sessionResult.value.title.value.isEmpty()) {
                            sessionService.updateTitle(id = sessionId, title = SessionTitle(value = text.take(n = 50)))
                        }
                    }
                    is Either.Left -> {}
                }
                handleLoadBranches()
            }
        }

        private fun handleLoadBranches() {
            val sessionId = state().sessionId ?: return
            scope.launch {
                val sessionResult = sessionService.get(id = sessionId)
                val isBranching = sessionResult is Either.Right && sessionResult.value.contextManagementType is ContextManagementType.Branching
                val branches = if (isBranching) {
                    when (val r = branchService.getAll(sessionId = sessionId)) {
                        is Either.Right -> r.value
                        is Either.Left -> emptyList()
                    }
                } else emptyList()
                val activeBranch = if (isBranching) {
                    when (val r = branchService.getActive(sessionId = sessionId)) {
                        is Either.Right -> r.value
                        is Either.Left -> null
                    }
                } else null
                val branchParentMap = if (isBranching) {
                    when (val r = branchService.getParentMap(sessionId = sessionId)) {
                        is Either.Right -> r.value
                        is Either.Left -> emptyMap()
                    }
                } else emptyMap()
                dispatch(Msg.BranchesLoaded(
                    branches = branches,
                    activeBranch = activeBranch,
                    isBranchingEnabled = isBranching,
                    branchParentMap = branchParentMap,
                ))
            }
        }

        private fun handleCreateBranch(name: String, parentTurnId: TurnId) {
            val sessionId = state().sessionId ?: return
            val activeBranch = state().activeBranch ?: return
            scope.launch {
                when (branchService.create(sessionId = sessionId, name = BranchName(value = name), parentTurnId = parentTurnId, fromBranchId = activeBranch.id)) {
                    is Either.Right -> handleLoadBranches()
                    is Either.Left -> {}
                }
            }
        }

        private fun handleSwitchBranch(branchId: BranchId) {
            val sessionId = state().sessionId ?: return
            dispatch(Msg.Loading)
            scope.launch {
                when (branchService.switch(sessionId = sessionId, branchId = branchId)) {
                    is Either.Right -> {
                        val history = when (val r = branchService.getActiveTurns(sessionId = sessionId)) {
                            is Either.Right -> r.value
                            is Either.Left -> emptyList()
                        }
                        val messages = history.flatMap { turn ->
                            listOf(
                                UiMessage(text = turn.userMessage.value, isUser = true, turnId = turn.id),
                                UiMessage(text = turn.assistantMessage.value, isUser = false, turnId = turn.id),
                            )
                        }
                        val turnUsage = when (val r = usageService.getBySession(sessionId = sessionId)) {
                            is Either.Right -> r.value
                            is Either.Left -> emptyMap()
                        }
                        val sessionUsage = turnUsage.values.fold(emptyUsageRecord()) { acc, u -> acc + u }
                        val branches = when (val r = branchService.getAll(sessionId = sessionId)) {
                            is Either.Right -> r.value
                            is Either.Left -> emptyList()
                        }
                        val activeBranch = when (val r = branchService.getActive(sessionId = sessionId)) {
                            is Either.Right -> r.value
                            is Either.Left -> null
                        }
                        val branchParentMap = when (val r = branchService.getParentMap(sessionId = sessionId)) {
                            is Either.Right -> r.value
                            is Either.Left -> emptyMap()
                        }
                        dispatch(Msg.BranchSwitched(
                            messages = messages,
                            activeBranch = activeBranch,
                            branches = branches,
                            turnUsage = turnUsage,
                            sessionUsage = sessionUsage,
                            branchParentMap = branchParentMap,
                        ))
                    }
                    is Either.Left -> {}
                }
                dispatch(Msg.LoadingComplete)
            }
        }

        private fun handleDeleteBranch(branchId: BranchId) {
            val sessionId = state().sessionId ?: return
            scope.launch {
                when (branchService.delete(branchId = branchId)) {
                    is Either.Right -> {
                        handleLoadBranches()
                        handleSwitchBranch(branchId = state().branches.first { it.isMain }.id)
                    }
                    is Either.Left -> {}
                }
            }
        }

        private fun emptyUsageRecord(): UsageRecord = UsageRecord(
            promptTokens = TokenCount(value = 0),
            completionTokens = TokenCount(value = 0),
            cachedTokens = TokenCount(value = 0),
            cacheWriteTokens = TokenCount(value = 0),
            reasoningTokens = TokenCount(value = 0),
            totalCost = Cost(value = BigDecimal.ZERO),
            upstreamCost = Cost(value = BigDecimal.ZERO),
            upstreamPromptCost = Cost(value = BigDecimal.ZERO),
            upstreamCompletionsCost = Cost(value = BigDecimal.ZERO),
        )
    }

    private object ReducerImpl : com.arkivanov.mvikotlin.core.store.Reducer<ChatStore.State, Msg> {
        override fun ChatStore.State.reduce(msg: Msg): ChatStore.State =
            when (msg) {
                is Msg.SessionLoaded -> copy(
                    sessionId = msg.sessionId,
                    messages = msg.messages,
                    turnUsage = msg.turnUsage,
                    sessionUsage = msg.sessionUsage,
                )
                is Msg.UserMessage -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = true),
                )
                is Msg.AgentResponseMsg -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = false, turnId = msg.turnId),
                    turnUsage = turnUsage + (msg.turnId to msg.usage),
                    sessionUsage = sessionUsage + msg.usage,
                )
                is Msg.Error -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = false, isError = true),
                )
                is Msg.Loading -> copy(isLoading = true)
                is Msg.LoadingComplete -> copy(isLoading = false)
                is Msg.BranchesLoaded -> copy(
                    branches = msg.branches,
                    activeBranch = msg.activeBranch,
                    isBranchingEnabled = msg.isBranchingEnabled,
                    branchParentMap = msg.branchParentMap,
                )
                is Msg.BranchSwitched -> copy(
                    messages = msg.messages,
                    activeBranch = msg.activeBranch,
                    branches = msg.branches,
                    turnUsage = msg.turnUsage,
                    sessionUsage = msg.sessionUsage,
                    branchParentMap = msg.branchParentMap,
                )
            }
    }
}
