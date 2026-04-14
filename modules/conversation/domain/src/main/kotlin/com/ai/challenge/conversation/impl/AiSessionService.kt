package com.ai.challenge.conversation.impl

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.conversation.model.AgentSession
import com.ai.challenge.conversation.model.Branch
import com.ai.challenge.conversation.model.SessionTitle
import com.ai.challenge.conversation.model.TurnSequence
import com.ai.challenge.conversation.repository.AgentSessionRepository
import com.ai.challenge.conversation.service.SessionService
import com.ai.challenge.sharedkernel.error.DomainError
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.vo.ContextModeId
import com.ai.challenge.sharedkernel.vo.CreatedAt
import com.ai.challenge.sharedkernel.vo.UpdatedAt
import kotlin.time.Clock

/**
 * Domain Service implementation -- [AgentSession] lifecycle management.
 *
 * Creates sessions with a default [ContextModeId], manages title updates,
 * context mode changes, and session deletion. Ensures main branch exists
 * when session is created.
 */
class AiSessionService(
    private val repository: AgentSessionRepository,
) : SessionService {

    override suspend fun create(title: SessionTitle): Either<DomainError, AgentSession> = either {
        val now = Clock.System.now()
        val session = AgentSession(
            id = AgentSessionId.generate(),
            title = title,
            contextModeId = ContextModeId(value = "none"),
            projectId = null,
            createdAt = CreatedAt(value = now),
            updatedAt = UpdatedAt(value = now),
        )
        val savedSession = repository.save(session = session)

        val mainBranch = Branch(
            id = BranchId.generate(),
            sessionId = savedSession.id,
            sourceTurnId = null,
            turnSequence = TurnSequence(values = emptyList()),
            createdAt = CreatedAt(value = now),
        )
        repository.createBranch(branch = mainBranch)

        savedSession
    }

    override suspend fun get(id: AgentSessionId): Either<DomainError, AgentSession> = either {
        repository.get(id = id)
            ?: raise(DomainError.SessionNotFound(id = id))
    }

    override suspend fun delete(id: AgentSessionId): Either<DomainError, Unit> = either {
        repository.get(id = id)
            ?: raise(DomainError.SessionNotFound(id = id))
        repository.delete(id = id)
    }

    override suspend fun list(): Either<DomainError, List<AgentSession>> =
        Either.Right(value = repository.list())

    override suspend fun updateTitle(
        id: AgentSessionId,
        title: SessionTitle,
    ): Either<DomainError, AgentSession> = either {
        val session = repository.get(id = id)
            ?: raise(DomainError.SessionNotFound(id = id))
        repository.update(session = session.withUpdatedTitle(newTitle = title))
    }

    override suspend fun updateContextModeId(
        id: AgentSessionId,
        contextModeId: ContextModeId,
    ): Either<DomainError, AgentSession> = either {
        val session = repository.get(id = id)
            ?: raise(DomainError.SessionNotFound(id = id))

        val updatedSession = repository.update(session = session.withContextModeId(contextModeId = contextModeId))

        updatedSession
    }
}
