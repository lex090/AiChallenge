package com.ai.challenge.pipeline

import com.ai.challenge.core.BranchRepository
import com.ai.challenge.core.ContextMiddleware
import com.ai.challenge.core.ContextState
import com.ai.challenge.core.TurnRepository

class BranchRoutingMiddleware(
    private val branchRepository: BranchRepository,
    private val turnRepository: TurnRepository,
) : ContextMiddleware {
    override suspend fun process(
        state: ContextState,
        next: suspend (ContextState) -> ContextState,
    ): ContextState {
        val activeBranchId = branchRepository.getActiveBranch(state.sessionId)
        if (activeBranchId != null) {
            val branch = branchRepository.getBranch(activeBranchId)
            if (branch != null) {
                val mainHistory = state.history.take(branch.checkpointTurnIndex)
                val branchTurns = branchRepository.getBranchTurns(activeBranchId)
                return next(
                    state.copy(
                        history = mainHistory + branchTurns,
                        activeBranchId = activeBranchId,
                    )
                )
            }
        }
        return next(state)
    }
}
