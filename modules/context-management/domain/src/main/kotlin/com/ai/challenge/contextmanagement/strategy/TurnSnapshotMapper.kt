package com.ai.challenge.contextmanagement.strategy

import com.ai.challenge.sharedkernel.vo.ContextMessage
import com.ai.challenge.sharedkernel.vo.MessageRole
import com.ai.challenge.sharedkernel.vo.TurnSnapshot

/**
 * Shared helper -- converts a list of turn snapshots into context messages.
 *
 * Each snapshot produces two messages: a User message followed by
 * an Assistant message, preserving chronological order.
 */
internal fun snapshotsToMessages(snapshots: List<TurnSnapshot>): List<ContextMessage> =
    snapshots.flatMap {
        listOf(
            ContextMessage(role = MessageRole.User, content = it.userMessage),
            ContextMessage(role = MessageRole.Assistant, content = it.assistantMessage),
        )
    }
