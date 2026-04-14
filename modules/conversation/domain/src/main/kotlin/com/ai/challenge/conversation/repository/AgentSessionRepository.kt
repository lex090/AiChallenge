package com.ai.challenge.conversation.repository

import com.ai.challenge.conversation.model.AgentSession
import com.ai.challenge.conversation.model.Branch
import com.ai.challenge.conversation.model.Turn
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.identity.ProjectId
import com.ai.challenge.sharedkernel.identity.TurnId

/**
 * Repository -- sole access point to the [AgentSession] aggregate
 * and its child entities [Branch] and [Turn].
 *
 * DDD rule: one repository per aggregate. All operations with
 * child entities go through this interface, not through separate
 * repositories. This guarantees aggregate invariants are checked
 * in one place.
 *
 * Implementation may internally use separate tables
 * (sessions, branches, turns, branch_turns), but the external API
 * operates only on domain models.
 */
interface AgentSessionRepository {
    suspend fun save(session: AgentSession): AgentSession
    suspend fun get(id: AgentSessionId): AgentSession?
    suspend fun delete(id: AgentSessionId)
    suspend fun list(): List<AgentSession>
    suspend fun update(session: AgentSession): AgentSession

    suspend fun createBranch(branch: Branch): Branch
    suspend fun getBranches(sessionId: AgentSessionId): List<Branch>
    suspend fun getBranch(branchId: BranchId): Branch?
    suspend fun getMainBranch(sessionId: AgentSessionId): Branch?
    suspend fun deleteBranch(branchId: BranchId)
    suspend fun deleteTurnsByBranch(branchId: BranchId)

    suspend fun appendTurn(branchId: BranchId, turn: Turn): Turn
    suspend fun getTurnsByBranch(branchId: BranchId): List<Turn>
    suspend fun getTurn(turnId: TurnId): Turn?

    suspend fun clearProjectId(projectId: ProjectId)
}
