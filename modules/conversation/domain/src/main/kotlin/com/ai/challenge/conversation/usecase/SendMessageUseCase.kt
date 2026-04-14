package com.ai.challenge.conversation.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.conversation.model.SessionTitle
import com.ai.challenge.conversation.model.Turn
import com.ai.challenge.conversation.service.ChatService
import com.ai.challenge.conversation.service.ProjectService
import com.ai.challenge.conversation.service.SessionService
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.event.DomainEvent
import com.ai.challenge.sharedkernel.event.DomainEventPublisher
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.vo.MessageContent
import com.ai.challenge.sharedkernel.vo.SystemInstructions
import com.ai.challenge.sharedkernel.vo.TurnSnapshot

/**
 * Application Service -- send message use case.
 *
 * Orchestrates:
 * 1. Loads session and resolves project instructions (if session belongs to a project)
 * 2. Delegates to [ChatService] for context preparation, LLM call, and Turn save
 * 3. Publishes [DomainEvent.TurnRecorded] event with [TurnSnapshot] for Context Management context
 * 4. Auto-generates session title from first message (if empty)
 *
 * Presentation layer calls this use case instead of [ChatService] directly.
 */
class SendMessageUseCase(
    private val chatService: ChatService,
    private val sessionService: SessionService,
    private val projectService: ProjectService,
    private val eventPublisher: DomainEventPublisher,
) {
    suspend fun execute(
        sessionId: AgentSessionId,
        branchId: BranchId,
        message: MessageContent,
    ): Either<DomainError, Turn> = either {
        val session = sessionService.get(id = sessionId).bind()

        val projectInstructions: SystemInstructions? = session.projectId?.let { pid ->
            projectService.get(id = pid).fold(
                ifLeft = { null },
                ifRight = { project -> project.systemInstructions },
            )
        }

        val turn = chatService.send(
            sessionId = sessionId,
            branchId = branchId,
            message = message,
            projectInstructions = projectInstructions,
        ).bind()

        val turnSnapshot = TurnSnapshot(
            turnId = turn.id,
            userMessage = turn.userMessage,
            assistantMessage = turn.assistantMessage,
        )
        eventPublisher.publish(event = DomainEvent.TurnRecorded(sessionId = sessionId, turnSnapshot = turnSnapshot, branchId = branchId))

        if (session.title.value.isEmpty()) {
            sessionService.updateTitle(id = sessionId, title = SessionTitle(value = message.value.take(n = 50)))
        }

        turn
    }
}
