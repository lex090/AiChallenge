package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.ai.challenge.core.Agent
import com.ai.challenge.core.AgentError
import com.ai.challenge.core.AgentResponse
import com.ai.challenge.core.AgentSession
import com.ai.challenge.core.Branch
import com.ai.challenge.core.BranchId
import com.ai.challenge.core.BranchNode
import com.ai.challenge.core.BranchRepository
import com.ai.challenge.core.BranchTree
import com.ai.challenge.core.CheckpointId
import com.ai.challenge.core.CheckpointNode
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
import com.ai.challenge.context.DelegatingContextManager
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.llm.model.ChatResponse

class AiAgent(
    private val service: OpenRouterService,
    private val model: String,
    private val sessionRepository: SessionRepository,
    private val turnRepository: TurnRepository,
    private val tokenRepository: TokenRepository,
    private val costRepository: CostRepository,
    private val contextManager: DelegatingContextManager,
    private val branchRepository: BranchRepository,
    private val factRepository: FactRepository,
) : Agent {

    override suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse> = either {
        val history = turnRepository.getBySession(sessionId)

        val context = catch({
            contextManager.prepareContext(sessionId, history, message)
        }) { e: Exception ->
            raise(AgentError.NetworkError(e.message ?: "Context preparation failed"))
        }

        val chatResponse = catch({
            service.chat(model = model) {
                for (msg in context.messages) {
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

        val activeBranch = if (getContextStrategyType() == ContextStrategyType.Branching) {
            branchRepository.getActiveBranch(sessionId)
        } else {
            null
        }
        val turnId = if (activeBranch != null) {
            branchRepository.appendTurnToBranch(activeBranch.id, turn)
        } else {
            turnRepository.append(sessionId, turn)
        }

        tokenRepository.record(sessionId, turnId, tokenDetails)
        costRepository.record(sessionId, turnId, costDetails)

        AgentResponse(text = text, turnId = turnId, tokenDetails = tokenDetails, costDetails = costDetails)
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

    override fun getContextStrategyType(): ContextStrategyType = contextManager.activeStrategy

    override fun setContextStrategy(type: ContextStrategyType) {
        contextManager.activeStrategy = type
    }

    override suspend fun createCheckpoint(sessionId: SessionId): Either<AgentError, CheckpointId> = either {
        val history = turnRepository.getBySession(sessionId)
        if (history.isEmpty()) {
            raise(AgentError.BranchError("Cannot create checkpoint for empty session"))
        }
        CheckpointId.generate()
    }

    override suspend fun createBranch(
        sessionId: SessionId,
        checkpointTurnIndex: Int,
        name: String,
    ): Either<AgentError, BranchId> = either {
        val history = turnRepository.getBySession(sessionId)
        if (checkpointTurnIndex < 0 || checkpointTurnIndex > history.size) {
            raise(AgentError.BranchError("Invalid checkpoint turn index: $checkpointTurnIndex"))
        }
        val branch = Branch(
            sessionId = sessionId,
            name = name,
            checkpointTurnIndex = checkpointTurnIndex,
        )
        branchRepository.createBranch(branch)
    }

    override suspend fun switchBranch(sessionId: SessionId, branchId: BranchId): Either<AgentError, Unit> = either {
        val branch = branchRepository.getBranch(branchId)
            ?: raise(AgentError.BranchError("Branch not found: ${branchId.value}"))
        if (branch.sessionId != sessionId) {
            raise(AgentError.BranchError("Branch does not belong to session"))
        }
        branchRepository.setActiveBranch(sessionId, branchId)
    }

    override suspend fun listBranches(sessionId: SessionId): Either<AgentError, List<Branch>> = either {
        branchRepository.getBranches(sessionId)
    }

    override suspend fun getBranchTree(sessionId: SessionId): Either<AgentError, BranchTree> = either {
        val branches = branchRepository.getBranches(sessionId)
        val activeBranch = branchRepository.getActiveBranch(sessionId)

        val checkpointGroups = branches.groupBy { it.checkpointTurnIndex }
        val checkpoints = checkpointGroups.entries
            .sortedBy { it.key }
            .map { (turnIndex, branchesAtCheckpoint) ->
                CheckpointNode(
                    turnIndex = turnIndex,
                    branches = branchesAtCheckpoint.map { branch ->
                        val turnCount = branchRepository.getTurnsForBranch(branch.id).size
                        BranchNode(
                            branch = branch,
                            turnCount = turnCount,
                            isActive = activeBranch?.id == branch.id,
                        )
                    },
                )
            }

        BranchTree(sessionId = sessionId, checkpoints = checkpoints)
    }

    override suspend fun getSessionFacts(sessionId: SessionId): Either<AgentError, List<Fact>> = either {
        factRepository.getBySession(sessionId)
    }
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
