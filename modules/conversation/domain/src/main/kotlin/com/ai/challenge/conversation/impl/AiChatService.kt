package com.ai.challenge.conversation.impl

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.ai.challenge.conversation.model.Cost
import com.ai.challenge.conversation.model.TokenCount
import com.ai.challenge.conversation.model.Turn
import com.ai.challenge.conversation.model.UsageRecord
import com.ai.challenge.conversation.repository.AgentSessionRepository
import com.ai.challenge.conversation.service.ChatService
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.identity.TurnId
import com.ai.challenge.sharedkernel.port.ContextManagerPort
import com.ai.challenge.sharedkernel.port.LlmPort
import com.ai.challenge.sharedkernel.vo.CreatedAt
import com.ai.challenge.sharedkernel.vo.MessageContent
import com.ai.challenge.sharedkernel.vo.ResponseFormat
import java.math.BigDecimal
import kotlin.time.Clock

/**
 * Domain Service implementation -- sends messages to AI agent via LLM.
 *
 * Orchestrates context preparation through [ContextManagerPort],
 * LLM completion through [LlmPort], and persists the resulting [Turn]
 * via [AgentSessionRepository].
 *
 * Maps [LlmUsage] from the shared kernel to Conversation's richer [UsageRecord].
 */
class AiChatService(
    private val llmPort: LlmPort,
    private val repository: AgentSessionRepository,
    private val contextManagerPort: ContextManagerPort,
) : ChatService {

    override suspend fun send(
        sessionId: AgentSessionId,
        branchId: BranchId,
        message: MessageContent,
    ): Either<DomainError, Turn> = either {
        val session = repository.get(id = sessionId)
            ?: raise(DomainError.SessionNotFound(id = sessionId))

        val preparedContext = catch({
            contextManagerPort.prepareContext(
                sessionId = sessionId,
                branchId = branchId,
                newMessage = message,
                contextModeId = session.contextModeId,
                projectInstructions = null,
            )
        }) { e: Exception ->
            raise(DomainError.NetworkError(message = e.message ?: "Context preparation failed"))
        }

        val llmResult = llmPort.complete(
            messages = preparedContext.messages,
            responseFormat = ResponseFormat.Text,
        ).bind()

        val usageRecord = UsageRecord(
            promptTokens = TokenCount(value = llmResult.usage.promptTokens),
            completionTokens = TokenCount(value = llmResult.usage.completionTokens),
            cachedTokens = TokenCount(value = 0),
            cacheWriteTokens = TokenCount(value = 0),
            reasoningTokens = TokenCount(value = 0),
            totalCost = Cost(value = BigDecimal.ZERO),
            upstreamCost = Cost(value = BigDecimal.ZERO),
            upstreamPromptCost = Cost(value = BigDecimal.ZERO),
            upstreamCompletionsCost = Cost(value = BigDecimal.ZERO),
        )

        val turn = Turn(
            id = TurnId.generate(),
            sessionId = sessionId,
            userMessage = message,
            assistantMessage = llmResult.content,
            usage = usageRecord,
            createdAt = CreatedAt(value = Clock.System.now()),
        )

        repository.appendTurn(branchId = branchId, turn = turn)
    }
}
