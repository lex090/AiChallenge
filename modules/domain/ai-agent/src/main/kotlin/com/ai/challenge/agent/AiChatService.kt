package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.ChatService
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.llm.LlmPort
import com.ai.challenge.core.llm.ResponseFormat
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import kotlin.time.Clock

class AiChatService(
    private val llmPort: LlmPort,
    private val repository: AgentSessionRepository,
    private val contextManager: ContextManager,
) : ChatService {

    override suspend fun send(
        sessionId: AgentSessionId,
        branchId: BranchId,
        message: MessageContent,
    ): Either<DomainError, Turn> = either {
        repository.get(id = sessionId)
            ?: raise(DomainError.SessionNotFound(id = sessionId))

        val context = catch({
            contextManager.prepareContext(sessionId = sessionId, branchId = branchId, newMessage = message)
        }) { e: Exception ->
            raise(DomainError.NetworkError(message = e.message ?: "Context preparation failed"))
        }

        val llmResult = llmPort.complete(
            messages = context.messages,
            responseFormat = ResponseFormat.Text,
        ).bind()

        val turn = Turn(
            id = TurnId.generate(),
            sessionId = sessionId,
            userMessage = message,
            assistantMessage = llmResult.content,
            usage = llmResult.usage,
            createdAt = CreatedAt(value = Clock.System.now()),
        )

        repository.appendTurn(branchId = branchId, turn = turn)
    }
}
