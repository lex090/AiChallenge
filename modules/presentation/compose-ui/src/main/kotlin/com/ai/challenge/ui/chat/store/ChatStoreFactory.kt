package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.core.agent.Agent
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.cost.CostDetails
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.token.TokenDetails
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class ChatStoreFactory(
    private val storeFactory: StoreFactory,
    private val agent: Agent,
) {
    fun create(): ChatStore =
        object : ChatStore,
            Store<ChatStore.Intent, ChatStore.State, Nothing> by storeFactory.create(
                name = "ChatStore",
                initialState = ChatStore.State(),
                executorFactory = { ExecutorImpl(agent) },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class SessionLoaded(
            val sessionId: AgentSessionId,
            val messages: List<UiMessage>,
            val turnTokens: Map<TurnId, TokenDetails>,
            val turnCosts: Map<TurnId, CostDetails>,
            val sessionTokens: TokenDetails,
            val sessionCosts: CostDetails,
        ) : Msg
        data class UserMessage(val text: String) : Msg
        data class AgentResponseMsg(
            val text: String,
            val turnId: TurnId,
            val tokenDetails: TokenDetails,
            val costDetails: CostDetails,
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
            val turnTokens: Map<TurnId, TokenDetails>,
            val turnCosts: Map<TurnId, CostDetails>,
            val sessionTokens: TokenDetails,
            val sessionCosts: CostDetails,
            val branchParentMap: Map<BranchId, BranchId?>,
        ) : Msg
    }

    private class ExecutorImpl(
        private val agent: Agent,
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
                val history = when (val r = agent.getActiveBranchTurns(sessionId = sessionId)) {
                    is Either.Right -> r.value
                    is Either.Left -> when (val t = agent.getTurns(sessionId = sessionId, limit = null)) {
                        is Either.Right -> t.value
                        is Either.Left -> emptyList()
                    }
                }
                val messages = history.flatMap { turn ->
                    listOf(
                        UiMessage(text = turn.userMessage, isUser = true, turnId = turn.id),
                        UiMessage(text = turn.agentResponse, isUser = false, turnId = turn.id),
                    )
                }
                val turnTokens = when (val r = agent.getTokensBySession(sessionId = sessionId)) {
                    is Either.Right -> r.value
                    is Either.Left -> emptyMap()
                }
                val turnCosts = when (val r = agent.getCostBySession(sessionId = sessionId)) {
                    is Either.Right -> r.value
                    is Either.Left -> emptyMap()
                }
                val sessionTokens = turnTokens.values.fold(TokenDetails(promptTokens = 0, completionTokens = 0, cachedTokens = 0, cacheWriteTokens = 0, reasoningTokens = 0)) { acc, t -> acc + t }
                val sessionCosts = turnCosts.values.fold(CostDetails(totalCost = 0.0, upstreamCost = 0.0, upstreamPromptCost = 0.0, upstreamCompletionsCost = 0.0)) { acc, c -> acc + c }
                dispatch(Msg.SessionLoaded(
                    sessionId = sessionId,
                    messages = messages,
                    turnTokens = turnTokens,
                    turnCosts = turnCosts,
                    sessionTokens = sessionTokens,
                    sessionCosts = sessionCosts,
                ))
                handleLoadBranches()
            }
        }

        private fun handleSendMessage(text: String) {
            val sessionId = state().sessionId ?: return
            dispatch(Msg.UserMessage(text = text))
            dispatch(Msg.Loading)

            scope.launch {
                when (val result = agent.send(sessionId = sessionId, message = text)) {
                    is Either.Right -> dispatch(
                        Msg.AgentResponseMsg(
                            text = result.value.text,
                            turnId = result.value.turnId,
                            tokenDetails = result.value.tokenDetails,
                            costDetails = result.value.costDetails,
                        )
                    )
                    is Either.Left -> dispatch(Msg.Error(text = result.value.message))
                }
                dispatch(Msg.LoadingComplete)

                when (val sessionResult = agent.getSession(id = sessionId)) {
                    is Either.Right -> {
                        if (sessionResult.value.title.isEmpty()) {
                            agent.updateSessionTitle(id = sessionId, title = text.take(n = 50))
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
                val typeResult = agent.getContextManagementType(sessionId = sessionId)
                val isBranching = typeResult is Either.Right && typeResult.value is ContextManagementType.Branching
                val branches = if (isBranching) {
                    when (val r = agent.getBranches(sessionId = sessionId)) {
                        is Either.Right -> r.value
                        is Either.Left -> emptyList()
                    }
                } else emptyList()
                val activeBranch = if (isBranching) {
                    when (val r = agent.getActiveBranch(sessionId = sessionId)) {
                        is Either.Right -> r.value
                        is Either.Left -> null
                    }
                } else null
                val branchParentMap = if (isBranching) {
                    when (val r = agent.getBranchParentMap(sessionId = sessionId)) {
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
                when (agent.createBranch(sessionId = sessionId, name = name, parentTurnId = parentTurnId, fromBranchId = activeBranch.id)) {
                    is Either.Right -> handleLoadBranches()
                    is Either.Left -> {}
                }
            }
        }

        private fun handleSwitchBranch(branchId: BranchId) {
            val sessionId = state().sessionId ?: return
            dispatch(Msg.Loading)
            scope.launch {
                when (agent.switchBranch(sessionId = sessionId, branchId = branchId)) {
                    is Either.Right -> {
                        val history = when (val r = agent.getActiveBranchTurns(sessionId = sessionId)) {
                            is Either.Right -> r.value
                            is Either.Left -> when (val t = agent.getTurns(sessionId = sessionId, limit = null)) {
                                is Either.Right -> t.value
                                is Either.Left -> emptyList()
                            }
                        }
                        val messages = history.flatMap { turn ->
                            listOf(
                                UiMessage(text = turn.userMessage, isUser = true, turnId = turn.id),
                                UiMessage(text = turn.agentResponse, isUser = false, turnId = turn.id),
                            )
                        }
                        val turnTokens = when (val r = agent.getTokensBySession(sessionId = sessionId)) {
                            is Either.Right -> r.value
                            is Either.Left -> emptyMap()
                        }
                        val turnCosts = when (val r = agent.getCostBySession(sessionId = sessionId)) {
                            is Either.Right -> r.value
                            is Either.Left -> emptyMap()
                        }
                        val sessionTokens = turnTokens.values.fold(TokenDetails(promptTokens = 0, completionTokens = 0, cachedTokens = 0, cacheWriteTokens = 0, reasoningTokens = 0)) { acc, t -> acc + t }
                        val sessionCosts = turnCosts.values.fold(CostDetails(totalCost = 0.0, upstreamCost = 0.0, upstreamPromptCost = 0.0, upstreamCompletionsCost = 0.0)) { acc, c -> acc + c }
                        val branches = when (val r = agent.getBranches(sessionId = sessionId)) {
                            is Either.Right -> r.value
                            is Either.Left -> emptyList()
                        }
                        val activeBranch = when (val r = agent.getActiveBranch(sessionId = sessionId)) {
                            is Either.Right -> r.value
                            is Either.Left -> null
                        }
                        val branchParentMap = when (val r = agent.getBranchParentMap(sessionId = sessionId)) {
                            is Either.Right -> r.value
                            is Either.Left -> emptyMap()
                        }
                        dispatch(Msg.BranchSwitched(
                            messages = messages,
                            activeBranch = activeBranch,
                            branches = branches,
                            turnTokens = turnTokens,
                            turnCosts = turnCosts,
                            sessionTokens = sessionTokens,
                            sessionCosts = sessionCosts,
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
                when (agent.deleteBranch(branchId = branchId)) {
                    is Either.Right -> {
                        handleLoadBranches()
                        handleSwitchBranch(branchId = state().branches.first { it.isMain }.id)
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
                    turnTokens = msg.turnTokens,
                    turnCosts = msg.turnCosts,
                    sessionTokens = msg.sessionTokens,
                    sessionCosts = msg.sessionCosts,
                )
                is Msg.UserMessage -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = true),
                )
                is Msg.AgentResponseMsg -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = false, turnId = msg.turnId),
                    turnTokens = turnTokens + (msg.turnId to msg.tokenDetails),
                    turnCosts = turnCosts + (msg.turnId to msg.costDetails),
                    sessionTokens = sessionTokens + msg.tokenDetails,
                    sessionCosts = sessionCosts + msg.costDetails,
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
                    turnTokens = msg.turnTokens,
                    turnCosts = msg.turnCosts,
                    sessionTokens = msg.sessionTokens,
                    sessionCosts = msg.sessionCosts,
                    branchParentMap = msg.branchParentMap,
                )
            }
    }
}
