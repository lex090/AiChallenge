package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.ai.challenge.core.agent.Agent
import com.ai.challenge.core.agent.AgentError
import com.ai.challenge.core.agent.AgentResponse
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

class AiAgent(
    private val service: OpenRouterService,
    private val model: String,
    private val sessionRepository: AgentSessionRepository,
    private val turnRepository: TurnRepository,
    private val tokenRepository: TokenDetailsRepository,
    private val costRepository: CostDetailsRepository,
    private val contextManager: ContextManager,
    private val contextManagementRepository: ContextManagementTypeRepository,
) : Agent {

    override suspend fun send(sessionId: AgentSessionId, message: String): Either<AgentError, AgentResponse> = either {
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
        val turnId = turnRepository.append(sessionId, turn)
        tokenRepository.record(sessionId, turnId, tokenDetails)
        costRepository.record(sessionId, turnId, costDetails)

        AgentResponse(text = text, turnId = turnId, tokenDetails = tokenDetails, costDetails = costDetails)
    }

    override suspend fun createSession(title: String): AgentSessionId {
        val sessionId = sessionRepository.create(title)
        contextManagementRepository.save(sessionId, ContextManagementType.None)
        return sessionId
    }

    override suspend fun deleteSession(id: AgentSessionId): Boolean {
        contextManagementRepository.delete(id)
        return sessionRepository.delete(id)
    }

    override suspend fun listSessions(): List<AgentSession> = sessionRepository.list()
    override suspend fun getSession(id: AgentSessionId): AgentSession? = sessionRepository.get(id)
    override suspend fun updateSessionTitle(id: AgentSessionId, title: String) = sessionRepository.updateTitle(id, title)
    override suspend fun getTurns(sessionId: AgentSessionId, limit: Int?): List<Turn> = turnRepository.getBySession(sessionId, limit)
    override suspend fun getTokensByTurn(turnId: TurnId): TokenDetails? = tokenRepository.getByTurn(turnId)
    override suspend fun getTokensBySession(sessionId: AgentSessionId): Map<TurnId, TokenDetails> = tokenRepository.getBySession(sessionId)
    override suspend fun getSessionTotalTokens(sessionId: AgentSessionId): TokenDetails = tokenRepository.getSessionTotal(sessionId)
    override suspend fun getCostByTurn(turnId: TurnId): CostDetails? = costRepository.getByTurn(turnId)
    override suspend fun getCostBySession(sessionId: AgentSessionId): Map<TurnId, CostDetails> = costRepository.getBySession(sessionId)
    override suspend fun getSessionTotalCost(sessionId: AgentSessionId): CostDetails = costRepository.getSessionTotal(sessionId)

    override suspend fun getContextManagementType(sessionId: AgentSessionId): Either<AgentError, ContextManagementType> =
        Either.Right(contextManagementRepository.getBySession(sessionId))

    override suspend fun updateContextManagementType(
        sessionId: AgentSessionId,
        type: ContextManagementType,
    ): Either<AgentError, Unit> {
        contextManagementRepository.save(sessionId, type)
        return Either.Right(Unit)
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
