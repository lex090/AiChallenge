package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.core.Agent
import com.ai.challenge.core.BranchId
import com.ai.challenge.core.BranchTree
import com.ai.challenge.core.ContextStrategyType
import com.ai.challenge.core.CostDetails
import com.ai.challenge.core.Fact
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.TokenDetails
import com.ai.challenge.core.TurnId
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
            val sessionId: SessionId,
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
        data class StrategyChanged(val strategy: ContextStrategyType) : Msg
        data class FactsLoaded(val facts: List<Fact>) : Msg
        data class BranchTreeLoaded(val branchTree: BranchTree) : Msg
    }

    private class ExecutorImpl(
        private val agent: Agent,
    ) : CoroutineExecutor<ChatStore.Intent, Nothing, ChatStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: ChatStore.Intent) {
            when (intent) {
                is ChatStore.Intent.SendMessage -> handleSendMessage(intent.text)
                is ChatStore.Intent.LoadSession -> handleLoadSession(intent.sessionId)
                is ChatStore.Intent.SwitchStrategy -> handleSwitchStrategy(intent.strategy)
                is ChatStore.Intent.CreateBranch -> handleCreateBranch(intent.checkpointTurnIndex, intent.name)
                is ChatStore.Intent.SwitchBranch -> handleSwitchBranch(intent.branchId)
                is ChatStore.Intent.SwitchToMain -> handleSwitchToMain()
                is ChatStore.Intent.LoadFacts -> handleLoadFacts()
                is ChatStore.Intent.LoadBranchTree -> handleLoadBranchTree()
            }
        }

        private fun handleLoadSession(sessionId: SessionId) {
            scope.launch {
                val history = agent.getEffectiveTurns(sessionId)
                val messages = history.flatMap { turn ->
                    listOf(
                        UiMessage(text = turn.userMessage, isUser = true, turnId = turn.id),
                        UiMessage(text = turn.agentResponse, isUser = false, turnId = turn.id),
                    )
                }
                val turnTokens = agent.getTokensBySession(sessionId)
                val turnCosts = agent.getCostBySession(sessionId)
                val sessionTokens = turnTokens.values.fold(TokenDetails()) { acc, t -> acc + t }
                val sessionCosts = turnCosts.values.fold(CostDetails()) { acc, c -> acc + c }
                dispatch(Msg.SessionLoaded(sessionId, messages, turnTokens, turnCosts, sessionTokens, sessionCosts))
                dispatch(Msg.StrategyChanged(agent.getContextStrategyType()))
            }
        }

        private fun handleSwitchStrategy(strategy: ContextStrategyType) {
            agent.setContextStrategy(strategy)
            dispatch(Msg.StrategyChanged(strategy))
        }

        private fun handleCreateBranch(checkpointTurnIndex: Int, name: String) {
            val sessionId = state().sessionId ?: return
            scope.launch {
                when (val result = agent.createBranch(sessionId, checkpointTurnIndex, name)) {
                    is Either.Right -> handleLoadBranchTree()
                    is Either.Left -> dispatch(Msg.Error(result.value.message))
                }
            }
        }

        private fun handleSwitchBranch(branchId: BranchId) {
            val sessionId = state().sessionId ?: return
            scope.launch {
                when (val result = agent.switchBranch(sessionId, branchId)) {
                    is Either.Right -> {
                        handleLoadBranchTree()
                        handleLoadSession(sessionId)
                    }
                    is Either.Left -> dispatch(Msg.Error(result.value.message))
                }
            }
        }

        private fun handleSwitchToMain() {
            val sessionId = state().sessionId ?: return
            scope.launch {
                when (val result = agent.deactivateBranch(sessionId)) {
                    is Either.Right -> {
                        handleLoadBranchTree()
                        handleLoadSession(sessionId)
                    }
                    is Either.Left -> dispatch(Msg.Error(result.value.message))
                }
            }
        }

        private fun handleLoadFacts() {
            val sessionId = state().sessionId ?: return
            scope.launch {
                when (val result = agent.getSessionFacts(sessionId)) {
                    is Either.Right -> dispatch(Msg.FactsLoaded(result.value))
                    is Either.Left -> dispatch(Msg.Error(result.value.message))
                }
            }
        }

        private fun handleLoadBranchTree() {
            val sessionId = state().sessionId ?: return
            scope.launch {
                when (val result = agent.getBranchTree(sessionId)) {
                    is Either.Right -> dispatch(Msg.BranchTreeLoaded(result.value))
                    is Either.Left -> dispatch(Msg.Error(result.value.message))
                }
            }
        }

        private fun handleSendMessage(text: String) {
            val sessionId = state().sessionId ?: return
            dispatch(Msg.UserMessage(text))
            dispatch(Msg.Loading)

            scope.launch {
                when (val result = agent.send(sessionId, text)) {
                    is Either.Right -> dispatch(
                        Msg.AgentResponseMsg(
                            text = result.value.text,
                            turnId = result.value.turnId,
                            tokenDetails = result.value.tokenDetails,
                            costDetails = result.value.costDetails,
                        )
                    )
                    is Either.Left -> dispatch(Msg.Error(result.value.message))
                }
                dispatch(Msg.LoadingComplete)

                val session = agent.getSession(sessionId)
                if (session != null && session.title.isEmpty()) {
                    agent.updateSessionTitle(sessionId, text.take(50))
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
                is Msg.StrategyChanged -> copy(currentStrategy = msg.strategy)
                is Msg.FactsLoaded -> copy(facts = msg.facts)
                is Msg.BranchTreeLoaded -> copy(branchTree = msg.branchTree)
            }
    }
}
