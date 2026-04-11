package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.ai.challenge.core.agent.Agent
import com.ai.challenge.core.agent.AgentError
import com.ai.challenge.core.agent.AgentResponse
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.BranchRepository
import com.ai.challenge.core.context.ContextManagementTypeRepository
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.cost.CostDetails
import com.ai.challenge.core.cost.CostDetailsRepository
import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.session.AgentSessionRepository
import com.ai.challenge.core.token.TokenDetails
import com.ai.challenge.core.token.TokenDetailsRepository
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.turn.TurnRepository
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.llm.model.ChatResponse
import kotlin.time.Clock

class AiAgent(
    private val service: OpenRouterService,
    private val model: String,
    private val sessionRepository: AgentSessionRepository,
    private val turnRepository: TurnRepository,
    private val tokenRepository: TokenDetailsRepository,
    private val costRepository: CostDetailsRepository,
    private val contextManager: ContextManager,
    private val contextManagementRepository: ContextManagementTypeRepository,
    private val branchRepository: BranchRepository,
) : Agent {

    override suspend fun send(sessionId: AgentSessionId, message: String): Either<AgentError, AgentResponse> = either {
        val context = catch({
            contextManager.prepareContext(sessionId = sessionId, newMessage = message)
        }) { e: Exception ->
            raise(AgentError.NetworkError(e.message ?: "Context preparation failed"))
        }

        val chatResponse = catch({
            service.chat(model = model) {
                for (msg in context.messages) {
                    message(role = msg.role.toApiRole(), content = msg.content)
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
        val turn = Turn(id = TurnId.generate(), sessionId = sessionId, userMessage = message, agentResponse = text, timestamp = Clock.System.now())
        val turnId = turnRepository.append(turn = turn)
        val contextType = contextManagementRepository.getBySession(sessionId = sessionId)
        if (contextType is ContextManagementType.Branching) {
            val activeBranch = branchRepository.getActiveBranch(sessionId = sessionId)
            if (activeBranch != null) {
                branchRepository.appendTurn(branchId = activeBranch.id, turnId = turnId)
            }
        }
        tokenRepository.record(sessionId = sessionId, turnId = turnId, details = tokenDetails)
        costRepository.record(sessionId = sessionId, turnId = turnId, details = costDetails)

        AgentResponse(text = text, turnId = turnId, tokenDetails = tokenDetails, costDetails = costDetails)
    }

    override suspend fun createSession(title: String): AgentSessionId {
        val sessionId = sessionRepository.create(title = title)
        contextManagementRepository.save(sessionId = sessionId, type = ContextManagementType.None)
        return sessionId
    }

    override suspend fun deleteSession(id: AgentSessionId): Boolean {
        contextManagementRepository.delete(sessionId = id)
        return sessionRepository.delete(id = id)
    }

    override suspend fun listSessions(): List<AgentSession> = sessionRepository.list()
    override suspend fun getSession(id: AgentSessionId): AgentSession? = sessionRepository.get(id = id)
    override suspend fun updateSessionTitle(id: AgentSessionId, title: String) = sessionRepository.updateTitle(id = id, title = title)
    override suspend fun getTurns(sessionId: AgentSessionId, limit: Int?): List<Turn> = turnRepository.getBySession(sessionId = sessionId, limit = limit)
    override suspend fun getTokensByTurn(turnId: TurnId): TokenDetails? = tokenRepository.getByTurn(turnId = turnId)
    override suspend fun getTokensBySession(sessionId: AgentSessionId): Map<TurnId, TokenDetails> = tokenRepository.getBySession(sessionId = sessionId)
    override suspend fun getSessionTotalTokens(sessionId: AgentSessionId): TokenDetails = tokenRepository.getSessionTotal(sessionId = sessionId)
    override suspend fun getCostByTurn(turnId: TurnId): CostDetails? = costRepository.getByTurn(turnId = turnId)
    override suspend fun getCostBySession(sessionId: AgentSessionId): Map<TurnId, CostDetails> = costRepository.getBySession(sessionId = sessionId)
    override suspend fun getSessionTotalCost(sessionId: AgentSessionId): CostDetails = costRepository.getSessionTotal(sessionId = sessionId)

    override suspend fun getContextManagementType(sessionId: AgentSessionId): Either<AgentError, ContextManagementType> =
        Either.Right(value = contextManagementRepository.getBySession(sessionId = sessionId))

    override suspend fun updateContextManagementType(
        sessionId: AgentSessionId,
        type: ContextManagementType,
    ): Either<AgentError, Unit> {
        contextManagementRepository.save(sessionId = sessionId, type = type)
        if (type is ContextManagementType.Branching) {
            val existing = branchRepository.getMainBranch(sessionId = sessionId)
            if (existing == null) {
                val mainBranch = Branch(
                    id = BranchId.generate(),
                    sessionId = sessionId,
                    name = "main",
                    parentBranchId = null,
                    isActive = true,
                    turnIds = emptyList(),
                    createdAt = Clock.System.now(),
                )
                branchRepository.create(branch = mainBranch)
                val turns = turnRepository.getBySession(sessionId = sessionId, limit = null)

                for (turn in turns) {
                    branchRepository.appendTurn(branchId = mainBranch.id, turnId = turn.id)
                }
            }
        }
        return Either.Right(value = Unit)
    }

    override suspend fun createBranch(
        sessionId: AgentSessionId,
        name: String,
        parentTurnId: TurnId,
        fromBranchId: BranchId,
    ): Either<AgentError, BranchId> = either {
        val type = contextManagementRepository.getBySession(sessionId = sessionId)
        if (type !is ContextManagementType.Branching) {
            raise(AgentError.ApiError(message = "Branching is not enabled for this session"))
        }
        val fromBranch = branchRepository.get(branchId = fromBranchId)
            ?: raise(AgentError.ApiError(message = "Source branch not found"))
        val parentTurnIds = fromBranch.turnIds
        val cutIndex = parentTurnIds.indexOf(element = parentTurnId)
        val trunkTurnIds = if (cutIndex >= 0) parentTurnIds.subList(fromIndex = 0, toIndex = cutIndex + 1) else parentTurnIds
        val branch = Branch(
            id = BranchId.generate(),
            sessionId = sessionId,
            name = name,
            parentBranchId = fromBranchId,
            isActive = false,
            turnIds = trunkTurnIds,
            createdAt = Clock.System.now(),
        )
        branchRepository.create(branch = branch)
        branch.id
    }

    override suspend fun deleteBranch(branchId: BranchId): Either<AgentError, Unit> = either {
        val branch = branchRepository.get(branchId = branchId)
            ?: raise(AgentError.ApiError(message = "Branch not found"))
        if (branch.isMain) {
            raise(AgentError.ApiError(message = "Cannot delete main branch"))
        }
        cascadeDeleteBranch(branchId = branchId, sessionId = branch.sessionId)
    }

    override suspend fun getBranches(sessionId: AgentSessionId): Either<AgentError, List<Branch>> =
        Either.Right(value = branchRepository.getBySession(sessionId = sessionId))

    override suspend fun switchBranch(
        sessionId: AgentSessionId,
        branchId: BranchId,
    ): Either<AgentError, Unit> = either {
        val branch = branchRepository.get(branchId = branchId)
            ?: raise(AgentError.ApiError(message = "Branch not found"))
        if (branch.sessionId != sessionId) {
            raise(AgentError.ApiError(message = "Branch does not belong to this session"))
        }
        branchRepository.setActive(sessionId = sessionId, branchId = branchId)
    }

    override suspend fun getActiveBranch(sessionId: AgentSessionId): Either<AgentError, Branch?> =
        Either.Right(value = branchRepository.getActiveBranch(sessionId = sessionId))

    override suspend fun getActiveBranchTurns(sessionId: AgentSessionId): Either<AgentError, List<Turn>> = either {
        val type = contextManagementRepository.getBySession(sessionId = sessionId)
        if (type !is ContextManagementType.Branching) {
            return@either turnRepository.getBySession(sessionId = sessionId, limit = null)
        }
        val activeBranch = branchRepository.getActiveBranch(sessionId = sessionId)
            ?: return@either turnRepository.getBySession(sessionId = sessionId, limit = null)
        activeBranch.turnIds.mapNotNull { turnRepository.get(turnId = it) }
    }

    override suspend fun getBranchParentMap(sessionId: AgentSessionId): Either<AgentError, Map<BranchId, BranchId?>> = either {
        val branches = branchRepository.getBySession(sessionId = sessionId)
        branches.associate { it.id to it.parentBranchId }
    }

    private suspend fun cascadeDeleteBranch(branchId: BranchId, sessionId: AgentSessionId) {
        val allBranches = branchRepository.getBySession(sessionId = sessionId)
        val childBranches = allBranches.filter { it.parentBranchId == branchId }

        for (child in childBranches) {
            cascadeDeleteBranch(branchId = child.id, sessionId = sessionId)
        }

        val wasActive = branchRepository.get(branchId = branchId)?.isActive ?: false
        branchRepository.deleteTurnsByBranch(branchId = branchId)
        branchRepository.delete(branchId = branchId)

        if (wasActive) {
            val mainBranch = branchRepository.getMainBranch(sessionId = sessionId)
            if (mainBranch != null) {
                branchRepository.setActive(sessionId = sessionId, branchId = mainBranch.id)
            }
        }
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
