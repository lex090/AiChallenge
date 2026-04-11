package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.ChatService
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.model.Cost
import com.ai.challenge.core.usage.model.TokenCount
import com.ai.challenge.core.usage.model.UsageRecord
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.llm.model.ChatResponse
import java.math.BigDecimal
import kotlin.time.Clock

class AiChatService(
    private val service: OpenRouterService,
    private val model: String,
    private val repository: AgentSessionRepository,
    private val contextManager: ContextManager,
) : ChatService {

    override suspend fun send(
        sessionId: AgentSessionId,
        message: MessageContent,
    ): Either<DomainError, Turn> = either {
        val session = repository.get(id = sessionId)
            ?: raise(DomainError.SessionNotFound(id = sessionId))

        val context = catch({
            contextManager.prepareContext(sessionId = sessionId, newMessage = message)
        }) { e: Exception ->
            raise(DomainError.NetworkError(message = e.message ?: "Context preparation failed"))
        }

        val chatResponse = catch({
            service.chat(model = model) {
                for (msg in context.messages) {
                    message(role = msg.role.toApiRole(), content = msg.content.value)
                }
            }
        }) { e: Exception ->
            val msg = e.message ?: "Unknown error"
            if (msg.startsWith("OpenRouter API error:")) {
                raise(DomainError.ApiError(message = msg.removePrefix("OpenRouter API error: ")))
            } else {
                raise(DomainError.NetworkError(message = msg))
            }
        }

        val error = chatResponse.error
        if (error != null) {
            raise(DomainError.ApiError(message = error.message ?: "Unknown API error"))
        }

        val text = chatResponse.choices.firstOrNull()?.message?.content
            ?: raise(DomainError.ApiError(message = "Empty response from OpenRouter"))

        val usage = chatResponse.toUsageRecord()
        val turn = Turn(
            id = TurnId.generate(),
            sessionId = sessionId,
            userMessage = message,
            assistantMessage = MessageContent(value = text),
            usage = usage,
            createdAt = CreatedAt(value = Clock.System.now()),
        )

        val branchId = if (session.contextManagementType is ContextManagementType.Branching) {
            session.activeBranchId
        } else {
            val mainBranch = repository.getMainBranch(sessionId = sessionId)
            mainBranch?.id ?: session.activeBranchId
        }

        repository.appendTurn(branchId = branchId, turn = turn)
    }
}

fun MessageRole.toApiRole(): String = when (this) {
    MessageRole.System -> "system"
    MessageRole.User -> "user"
    MessageRole.Assistant -> "assistant"
}

private fun ChatResponse.toUsageRecord(): UsageRecord = UsageRecord(
    promptTokens = TokenCount(value = usage?.promptTokens ?: 0),
    completionTokens = TokenCount(value = usage?.completionTokens ?: 0),
    cachedTokens = TokenCount(value = usage?.promptTokensDetails?.cachedTokens ?: 0),
    cacheWriteTokens = TokenCount(value = usage?.promptTokensDetails?.cacheWriteTokens ?: 0),
    reasoningTokens = TokenCount(value = usage?.completionTokensDetails?.reasoningTokens ?: 0),
    totalCost = Cost(value = BigDecimal.valueOf(usage?.cost ?: cost ?: 0.0)),
    upstreamCost = Cost(value = BigDecimal.valueOf(usage?.costDetails?.upstreamCost ?: costDetails?.upstreamCost ?: 0.0)),
    upstreamPromptCost = Cost(value = BigDecimal.valueOf(usage?.costDetails?.upstreamPromptCost ?: costDetails?.upstreamPromptCost ?: 0.0)),
    upstreamCompletionsCost = Cost(value = BigDecimal.valueOf(usage?.costDetails?.upstreamCompletionsCost ?: costDetails?.upstreamCompletionsCost ?: 0.0)),
)
