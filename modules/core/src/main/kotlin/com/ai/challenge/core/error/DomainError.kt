package com.ai.challenge.core.error

import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.TurnId

/**
 * Value Object — sealed hierarchy of domain errors.
 *
 * Used with Arrow Either at domain boundaries:
 * `Either<DomainError, T>`. Presentation layer pattern-matches
 * on error type — no try/catch.
 *
 * Infrastructure errors: [NetworkError], [ApiError], [DatabaseError]
 * Resource errors: [SessionNotFound], [BranchNotFound], [TurnNotFound]
 * Business rule violations: [MainBranchCannotBeDeleted],
 *   [BranchingNotEnabled], [BranchNotOwnedBySession]
 */
sealed interface DomainError {
    val message: String

    data class NetworkError(override val message: String) : DomainError
    data class ApiError(override val message: String) : DomainError
    data class DatabaseError(override val message: String) : DomainError

    data class SessionNotFound(val id: AgentSessionId) : DomainError {
        override val message: String get() = "Session not found: ${id.value}"
    }

    data class BranchNotFound(val id: BranchId) : DomainError {
        override val message: String get() = "Branch not found: ${id.value}"
    }

    data class TurnNotFound(val id: TurnId) : DomainError {
        override val message: String get() = "Turn not found: ${id.value}"
    }

    data class MainBranchCannotBeDeleted(val sessionId: AgentSessionId) : DomainError {
        override val message: String get() = "Cannot delete main branch for session: ${sessionId.value}"
    }

    data class BranchingNotEnabled(val sessionId: AgentSessionId) : DomainError {
        override val message: String get() = "Branching is not enabled for session: ${sessionId.value}"
    }

    data class BranchNotOwnedBySession(val branchId: BranchId, val sessionId: AgentSessionId) : DomainError {
        override val message: String get() = "Branch ${branchId.value} does not belong to session ${sessionId.value}"
    }
}
