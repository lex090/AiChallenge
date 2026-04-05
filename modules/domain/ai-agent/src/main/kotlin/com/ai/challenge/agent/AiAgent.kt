package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.catch
import arrow.core.raise.either
import com.ai.challenge.core.Agent
import com.ai.challenge.core.AgentError
import com.ai.challenge.core.AgentResponse
import com.ai.challenge.core.AgentSession
import com.ai.challenge.core.Branch
import com.ai.challenge.core.BranchId
import com.ai.challenge.core.BranchRepository
import com.ai.challenge.core.BranchTree
import com.ai.challenge.core.BranchableStrategy
import com.ai.challenge.core.CheckpointId
import com.ai.challenge.core.ContextStrategy
import com.ai.challenge.core.ContextStrategyType
import com.ai.challenge.core.CostDetails
import com.ai.challenge.core.CostRepository
import com.ai.challenge.core.Fact
import com.ai.challenge.core.FactRepository
import com.ai.challenge.core.MessageRole
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.SessionRepository
import com.ai.challenge.core.TokenDetails
import com.ai.challenge.core.TokenRepository
import com.ai.challenge.core.Turn
import com.ai.challenge.core.TurnId
import com.ai.challenge.core.TurnRepository
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.llm.model.ChatResponse

class AiAgent(
    private val service: OpenRouterService,
    private val model: String,
    private val sessionRepository: SessionRepository,
    private val turnRepository: TurnRepository,
    private val tokenRepository: TokenRepository,
    private val costRepository: CostRepository,
    private val strategies: Map<ContextStrategyType, ContextStrategy>,
    private val branchRepository: BranchRepository? = null,
    private val factRepository: FactRepository? = null,
) : Agent {

    private var activeStrategyType: ContextStrategyType = ContextStrategyType.SlidingWindow
    private val activeStrategy: ContextStrategy get() = strategies.getValue(activeStrategyType)

    override suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse> = either {
        val history = turnRepository.getBySession(sessionId)

        val messages = catch({
            activeStrategy.buildMessages(sessionId, history, message)
        }) { e: Exception ->
            raise(AgentError.NetworkError(e.message ?: "Context preparation failed"))
        }

        val chatResponse = catch({
            service.chat(model = model) {
                for (msg in messages) {
                    message(msg.role.toApiRole(), msg.content)
                }
            }
        }) { e: Exception ->
            val msg = e.message ?: "Unknown error"
            if (msg.startsWith("OpenRouter API error:")) {
                raise(AgentError.ApiError(msg.removePrefix("OpenRouter API error: ")))
            } else {
                raise(AgentError.NetworkError(msg))
            }
        }

        val error = chatResponse.error
        if (error != null) {
            raise(AgentError.ApiError(error.message ?: "Unknown API error"))
        }

        val text = chatResponse.choices.firstOrNull()?.message?.content
            ?: raise(AgentError.ApiError("Empty response from OpenRouter"))

        val tokenDetails = chatResponse.toTokenDetails()
        val costDetails = chatResponse.toCostDetails()
        val turn = Turn(userMessage = message, agentResponse = text)
        val turnId = turnRepository.append(sessionId, turn)

        if (activeStrategyType == ContextStrategyType.Branching && branchRepository != null) {
            val activeBranch = branchRepository.getActiveBranch(sessionId)
            if (activeBranch != null) {
                val existingTurnIds = branchRepository.getBranchTurnIds(activeBranch.id)
                branchRepository.saveBranchTurns(activeBranch.id, existingTurnIds + turnId)
            }
        }

        tokenRepository.record(sessionId, turnId, tokenDetails)
        costRepository.record(sessionId, turnId, costDetails)

        AgentResponse(text = text, turnId = turnId, tokenDetails = tokenDetails, costDetails = costDetails)
    }

    override fun getContextStrategyType(): ContextStrategyType = activeStrategyType

    override fun setContextStrategy(type: ContextStrategyType) {
        require(strategies.containsKey(type)) { "Strategy $type is not configured" }
        activeStrategyType = type
    }

    override suspend fun createCheckpoint(sessionId: SessionId): Either<AgentError, CheckpointId> {
        val strategy = activeStrategy
        if (strategy !is BranchableStrategy) {
            return AgentError.StrategyError("Current strategy does not support branching").left()
        }
        return strategy.createCheckpoint(sessionId)
    }

    override suspend fun createBranch(
        sessionId: SessionId,
        checkpointTurnIndex: Int,
        name: String,
    ): Either<AgentError, BranchId> {
        val strategy = activeStrategy
        if (strategy !is BranchableStrategy) {
            return AgentError.StrategyError("Current strategy does not support branching").left()
        }
        return strategy.createBranch(sessionId, checkpointTurnIndex, name)
    }

    override suspend fun switchBranch(sessionId: SessionId, branchId: BranchId): Either<AgentError, Unit> {
        val strategy = activeStrategy
        if (strategy !is BranchableStrategy) {
            return AgentError.StrategyError("Current strategy does not support branching").left()
        }
        return strategy.switchBranch(sessionId, branchId)
    }

    override suspend fun listBranches(sessionId: SessionId): Either<AgentError, List<Branch>> {
        val strategy = activeStrategy
        if (strategy !is BranchableStrategy) {
            return AgentError.StrategyError("Current strategy does not support branching").left()
        }
        return strategy.listBranches(sessionId)
    }

    override suspend fun getBranchTree(sessionId: SessionId): Either<AgentError, BranchTree> {
        val strategy = activeStrategy
        if (strategy !is BranchableStrategy) {
            return AgentError.StrategyError("Current strategy does not support branching").left()
        }
        return strategy.getBranchTree(sessionId)
    }

    override suspend fun getSessionFacts(sessionId: SessionId): Either<AgentError, List<Fact>> {
        val repo = factRepository
            ?: return AgentError.StrategyError("Fact repository is not configured").left()
        return Either.Right(repo.getBySession(sessionId))
    }

    override suspend fun createSession(title: String): SessionId = sessionRepository.create(title)
    override suspend fun deleteSession(id: SessionId): Boolean = sessionRepository.delete(id)
    override suspend fun listSessions(): List<AgentSession> = sessionRepository.list()
    override suspend fun getSession(id: SessionId): AgentSession? = sessionRepository.get(id)
    override suspend fun updateSessionTitle(id: SessionId, title: String) = sessionRepository.updateTitle(id, title)
    override suspend fun getTurns(sessionId: SessionId, limit: Int?): List<Turn> = turnRepository.getBySession(sessionId, limit)
    override suspend fun getTokensByTurn(turnId: TurnId): TokenDetails? = tokenRepository.getByTurn(turnId)
    override suspend fun getTokensBySession(sessionId: SessionId): Map<TurnId, TokenDetails> = tokenRepository.getBySession(sessionId)
    override suspend fun getSessionTotalTokens(sessionId: SessionId): TokenDetails = tokenRepository.getSessionTotal(sessionId)
    override suspend fun getCostByTurn(turnId: TurnId): CostDetails? = costRepository.getByTurn(turnId)
    override suspend fun getCostBySession(sessionId: SessionId): Map<TurnId, CostDetails> = costRepository.getBySession(sessionId)
    override suspend fun getSessionTotalCost(sessionId: SessionId): CostDetails = costRepository.getSessionTotal(sessionId)
}

fun MessageRole.toApiRole(): String = when (this) {
    MessageRole.System -> "system"
    MessageRole.User -> "user"
    MessageRole.Assistant -> "assistant"
}

private fun ChatResponse.toTokenDetails(): TokenDetails = TokenDetails(
    promptTokens = usage?.promptTokens ?: 0,
    completionTokens = usage?.completionTokens ?: 0,
    cachedTokens = usage?.promptTokensDetails?.cachedTokens ?: 0,
    cacheWriteTokens = usage?.promptTokensDetails?.cacheWriteTokens ?: 0,
    reasoningTokens = usage?.completionTokensDetails?.reasoningTokens ?: 0,
)

private fun ChatResponse.toCostDetails(): CostDetails = CostDetails(
    totalCost = usage?.cost ?: cost ?: 0.0,
    upstreamCost = usage?.costDetails?.upstreamCost ?: costDetails?.upstreamCost ?: 0.0,
    upstreamPromptCost = usage?.costDetails?.upstreamPromptCost ?: costDetails?.upstreamPromptCost ?: 0.0,
    upstreamCompletionsCost = usage?.costDetails?.upstreamCompletionsCost ?: costDetails?.upstreamCompletionsCost ?: 0.0,
)
