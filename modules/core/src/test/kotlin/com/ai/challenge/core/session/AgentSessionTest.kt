package com.ai.challenge.core.session

import arrow.core.Either
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.branch.TurnSequence
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.shared.UpdatedAt
import com.ai.challenge.core.turn.TurnId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock

class AgentSessionTest {

    private val sessionId = AgentSessionId(value = "test-session-id")

    private val session = AgentSession(
        id = sessionId,
        title = SessionTitle(value = "Test"),
        contextManagementType = ContextManagementType.None,
        createdAt = CreatedAt(value = Clock.System.now()),
        updatedAt = UpdatedAt(value = Clock.System.now()),
    )

    @Test
    fun `ensureBranchDeletable returns Left for main branch`() {
        val mainBranch = Branch(
            id = BranchId(value = "main-branch-id"),
            sessionId = sessionId,
            sourceTurnId = null,
            turnSequence = TurnSequence(values = emptyList()),
            createdAt = CreatedAt(value = Clock.System.now()),
        )

        val result = session.ensureBranchDeletable(branch = mainBranch)

        assertIs<Either.Left<DomainError.MainBranchCannotBeDeleted>>(value = result)
        assertEquals(expected = sessionId, actual = result.value.sessionId)
    }

    @Test
    fun `ensureBranchDeletable returns Right for non-main branch`() {
        val nonMainBranch = Branch(
            id = BranchId(value = "feature-branch-id"),
            sessionId = sessionId,
            sourceTurnId = TurnId(value = "source-turn-id"),
            turnSequence = TurnSequence(values = emptyList()),
            createdAt = CreatedAt(value = Clock.System.now()),
        )

        val result = session.ensureBranchDeletable(branch = nonMainBranch)

        assertIs<Either.Right<Unit>>(value = result)
    }
}
