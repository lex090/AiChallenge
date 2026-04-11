package com.ai.challenge.core.chat

import arrow.core.Either
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.model.BranchName
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId

interface BranchService {
    suspend fun create(sessionId: AgentSessionId, name: BranchName, parentTurnId: TurnId, fromBranchId: BranchId): Either<DomainError, Branch>
    suspend fun delete(branchId: BranchId): Either<DomainError, Unit>
    suspend fun switch(sessionId: AgentSessionId, branchId: BranchId): Either<DomainError, Unit>
    suspend fun getAll(sessionId: AgentSessionId): Either<DomainError, List<Branch>>
    suspend fun getActive(sessionId: AgentSessionId): Either<DomainError, Branch>
    suspend fun getActiveTurns(sessionId: AgentSessionId): Either<DomainError, List<Turn>>
    suspend fun getParentMap(sessionId: AgentSessionId): Either<DomainError, Map<BranchId, BranchId?>>
}
