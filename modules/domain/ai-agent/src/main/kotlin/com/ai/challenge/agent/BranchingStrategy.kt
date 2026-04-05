package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ai.challenge.core.AgentError
import com.ai.challenge.core.Branch
import com.ai.challenge.core.BranchId
import com.ai.challenge.core.BranchNode
import com.ai.challenge.core.BranchRepository
import com.ai.challenge.core.BranchTree
import com.ai.challenge.core.BranchableStrategy
import com.ai.challenge.core.CheckpointId
import com.ai.challenge.core.CheckpointNode
import com.ai.challenge.core.ContextMessage
import com.ai.challenge.core.ContextStrategyType
import com.ai.challenge.core.MessageRole
import com.ai.challenge.core.SessionId
import com.ai.challenge.core.Turn
import com.ai.challenge.core.TurnId
import com.ai.challenge.core.TurnRepository
import kotlin.time.Clock

class BranchingStrategy(
    private val branchRepository: BranchRepository,
    private val turnRepository: TurnRepository,
    private val windowSize: Int = 10,
) : BranchableStrategy {

    override val type: ContextStrategyType = ContextStrategyType.Branching

    override suspend fun buildMessages(
        sessionId: SessionId,
        history: List<Turn>,
        newMessage: String,
    ): List<ContextMessage> {
        val activeBranch = branchRepository.getActiveBranch(sessionId)

        val effectiveHistory = if (activeBranch != null) {
            val branchTurnIds = branchRepository.getBranchTurnIds(activeBranch.id)
            if (branchTurnIds.isNotEmpty()) {
                val baseTurns = history.take(activeBranch.checkpointTurnIndex)
                val branchTurns = branchTurnIds.mapNotNull { turnRepository.get(it) }
                baseTurns + branchTurns
            } else {
                history.take(activeBranch.checkpointTurnIndex)
            }
        } else {
            history
        }

        val recent = effectiveHistory.takeLast(windowSize)
        return recent.flatMap { turn ->
            listOf(
                ContextMessage(MessageRole.User, turn.userMessage),
                ContextMessage(MessageRole.Assistant, turn.agentResponse),
            )
        } + ContextMessage(MessageRole.User, newMessage)
    }

    override suspend fun createCheckpoint(sessionId: SessionId): Either<AgentError, CheckpointId> {
        val history = turnRepository.getBySession(sessionId)
        if (history.isEmpty()) {
            return AgentError.StrategyError("Cannot create checkpoint on empty session").left()
        }
        return CheckpointId.generate().right()
    }

    override suspend fun createBranch(
        sessionId: SessionId,
        checkpointTurnIndex: Int,
        name: String,
    ): Either<AgentError, BranchId> {
        val history = turnRepository.getBySession(sessionId)
        if (checkpointTurnIndex < 0 || checkpointTurnIndex > history.size) {
            return AgentError.StrategyError(
                "Checkpoint turn index $checkpointTurnIndex out of range (0..${history.size})",
            ).left()
        }

        val branch = Branch(
            id = BranchId.generate(),
            sessionId = sessionId,
            name = name,
            parentBranchId = branchRepository.getActiveBranch(sessionId)?.id,
            checkpointTurnIndex = checkpointTurnIndex,
            isActive = false,
            createdAt = Clock.System.now(),
        )

        val branchId = branchRepository.createBranch(branch)
        branchRepository.setActiveBranch(sessionId, branchId)
        return branchId.right()
    }

    override suspend fun switchBranch(
        sessionId: SessionId,
        branchId: BranchId,
    ): Either<AgentError, Unit> {
        val branch = branchRepository.getBranch(branchId)
            ?: return AgentError.StrategyError("Branch not found: ${branchId.value}").left()

        if (branch.sessionId != sessionId) {
            return AgentError.StrategyError("Branch does not belong to this session").left()
        }

        branchRepository.setActiveBranch(sessionId, branchId)
        return Unit.right()
    }

    override suspend fun getActiveBranch(sessionId: SessionId): Either<AgentError, Branch?> {
        return branchRepository.getActiveBranch(sessionId).right()
    }

    override suspend fun listBranches(sessionId: SessionId): Either<AgentError, List<Branch>> {
        return branchRepository.getBySession(sessionId).right()
    }

    override suspend fun getBranchTree(sessionId: SessionId): Either<AgentError, BranchTree> {
        val branches = branchRepository.getBySession(sessionId)
        val activeBranch = branchRepository.getActiveBranch(sessionId)

        val checkpointGroups = branches.groupBy { it.checkpointTurnIndex }
        val checkpoints = checkpointGroups.entries
            .sortedBy { it.key }
            .map { (turnIndex, branchesAtCheckpoint) ->
                CheckpointNode(
                    turnIndex = turnIndex,
                    branches = branchesAtCheckpoint.map { branch ->
                        val turnCount = branchRepository.getBranchTurnIds(branch.id).size
                        BranchNode(
                            branch = branch,
                            turnCount = turnCount,
                            isActive = activeBranch?.id == branch.id,
                        )
                    },
                )
            }

        return BranchTree(sessionId = sessionId, checkpoints = checkpoints).right()
    }
}
