package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.BranchService
import com.ai.challenge.core.chat.ChatService
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.UsageService
import com.ai.challenge.core.usage.model.UsageRecord
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

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
            val branches: List<Branch>,
            val activeBranchId: BranchId?,
            val isBranchingEnabled: Boolean,
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
            val activeBranchId: BranchId?,
            val isBranchingEnabled: Boolean,
        ) : Msg
        data class BranchSwitched(
            val messages: List<UiMessage>,
            val activeBranchId: BranchId?,
            val branches: List<Branch>,
            val turnUsage: Map<TurnId, UsageRecord>,
            val sessionUsage: UsageRecord,
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
                is ChatStore.Intent.CreateBranch -> handleCreateBranch(sourceTurnId = intent.sourceTurnId)
                is ChatStore.Intent.SwitchBranch -> handleSwitchBranch(branchId = intent.branchId)
                is ChatStore.Intent.DeleteBranch -> handleDeleteBranch(branchId = intent.branchId)
                is ChatStore.Intent.LoadBranches -> handleLoadBranches()
            }
        }

        private fun handleLoadSession(sessionId: AgentSessionId) {
            scope.launch {
                val sessionResult = sessionService.get(id = sessionId)
                val isBranching = sessionResult is Either.Right && sessionResult.value.contextManagementType is ContextManagementType.Branching

                val branches = when (val r = branchService.getAll(sessionId = sessionId)) {
                    is Either.Right -> r.value
                    is Either.Left -> emptyList()
                }

                val mainBranchId = branches.firstOrNull { it.isMain }?.id
                val activeBranchId = mainBranchId

                val history = if (activeBranchId != null) {
                    when (val r = branchService.getTurns(branchId = activeBranchId)) {
                        is Either.Right -> r.value
                        is Either.Left -> emptyList()
                    }
                } else emptyList()

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
                val sessionUsage = turnUsage.values.fold(UsageRecord.ZERO) { acc, u -> acc + u }
                dispatch(Msg.SessionLoaded(
                    sessionId = sessionId,
                    messages = messages,
                    turnUsage = turnUsage,
                    sessionUsage = sessionUsage,
                    branches = branches,
                    activeBranchId = activeBranchId,
                    isBranchingEnabled = isBranching,
                ))
            }
        }

        private fun handleSendMessage(text: String) {
            val sessionId = state().sessionId ?: return
            val branchId = state().activeBranchId ?: return
            dispatch(Msg.UserMessage(text = text))
            dispatch(Msg.Loading)

            scope.launch {
                when (val result = chatService.send(sessionId = sessionId, branchId = branchId, message = MessageContent(value = text))) {
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
            }
        }

        private fun handleLoadBranches() {
            val sessionId = state().sessionId ?: return
            scope.launch {
                val sessionResult = sessionService.get(id = sessionId)
                val isBranching = sessionResult is Either.Right && sessionResult.value.contextManagementType is ContextManagementType.Branching
                val branches = when (val r = branchService.getAll(sessionId = sessionId)) {
                    is Either.Right -> r.value
                    is Either.Left -> emptyList()
                }
                dispatch(Msg.BranchesLoaded(
                    branches = branches,
                    activeBranchId = state().activeBranchId,
                    isBranchingEnabled = isBranching,
                ))
            }
        }

        private fun handleCreateBranch(sourceTurnId: TurnId) {
            val sessionId = state().sessionId ?: return
            val activeBranchId = state().activeBranchId ?: return
            scope.launch {
                when (val result = branchService.create(sessionId = sessionId, sourceTurnId = sourceTurnId, fromBranchId = activeBranchId)) {
                    is Either.Right -> {
                        val newBranch = result.value
                        handleSwitchBranch(branchId = newBranch.id)
                    }
                    is Either.Left -> {}
                }
            }
        }

        private fun handleSwitchBranch(branchId: BranchId) {
            val sessionId = state().sessionId ?: return
            dispatch(Msg.Loading)
            scope.launch {
                val history = when (val r = branchService.getTurns(branchId = branchId)) {
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
                val sessionUsage = turnUsage.values.fold(UsageRecord.ZERO) { acc, u -> acc + u }
                val branches = when (val r = branchService.getAll(sessionId = sessionId)) {
                    is Either.Right -> r.value
                    is Either.Left -> emptyList()
                }
                dispatch(Msg.BranchSwitched(
                    messages = messages,
                    activeBranchId = branchId,
                    branches = branches,
                    turnUsage = turnUsage,
                    sessionUsage = sessionUsage,
                ))
                dispatch(Msg.LoadingComplete)
            }
        }

        private fun handleDeleteBranch(branchId: BranchId) {
            val sessionId = state().sessionId ?: return
            scope.launch {
                when (branchService.delete(branchId = branchId)) {
                    is Either.Right -> {
                        val isActive = state().activeBranchId == branchId
                        if (isActive) {
                            val branches = when (val r = branchService.getAll(sessionId = sessionId)) {
                                is Either.Right -> r.value
                                is Either.Left -> emptyList()
                            }
                            val mainId = branches.firstOrNull { it.isMain }?.id
                            if (mainId != null) {
                                handleSwitchBranch(branchId = mainId)
                            }
                        } else {
                            handleLoadBranches()
                        }
                    }
                    is Either.Left -> {}
                }
            }
        }

    }

    private object ReducerImpl : com.arkivanov.mvikotlin.core.store.Reducer<ChatStore.State, Msg> {
        override fun ChatStore.State.reduce(msg: Msg): ChatStore.State =
            when (msg) {
                is Msg.SessionLoaded -> copy(
                    sessionId = msg.sessionId,
                    messages = msg.messages,
                    turnUsage = msg.turnUsage,
                    sessionUsage = msg.sessionUsage,
                    branches = msg.branches,
                    activeBranchId = msg.activeBranchId,
                    isBranchingEnabled = msg.isBranchingEnabled,
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
                    activeBranchId = msg.activeBranchId,
                    isBranchingEnabled = msg.isBranchingEnabled,
                )
                is Msg.BranchSwitched -> copy(
                    messages = msg.messages,
                    activeBranchId = msg.activeBranchId,
                    branches = msg.branches,
                    turnUsage = msg.turnUsage,
                    sessionUsage = msg.sessionUsage,
                )
            }
    }
}
