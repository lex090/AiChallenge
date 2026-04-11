package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.either
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.TurnSequence
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.shared.UpdatedAt
import kotlin.time.Clock

class AiSessionService(
    private val repository: AgentSessionRepository,
) : SessionService {

    override suspend fun create(title: SessionTitle): Either<DomainError, AgentSession> = either {
        val now = Clock.System.now()
        val session = AgentSession(
            id = AgentSessionId.generate(),
            title = title,
            contextManagementType = ContextManagementType.None,
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

    override suspend fun updateContextManagementType(
        id: AgentSessionId,
        type: ContextManagementType,
    ): Either<DomainError, AgentSession> = either {
        val session = repository.get(id = id)
            ?: raise(DomainError.SessionNotFound(id = id))

        val updatedSession = repository.update(session = session.withContextManagementType(type = type))

        if (type is ContextManagementType.Branching) {
            val existingMain = repository.getMainBranch(sessionId = id)
            if (existingMain == null) {
                val now = Clock.System.now()
                val mainBranch = Branch(
                    id = BranchId.generate(),
                    sessionId = id,
                    sourceTurnId = null,
                    turnSequence = TurnSequence(values = emptyList()),
                    createdAt = CreatedAt(value = now),
                )
                repository.createBranch(branch = mainBranch)
            }
        }

        updatedSession
    }
}
