package com.ai.challenge.context

import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.turn.Turn

/**
 * Shared helper — converts a list of turns into context messages.
 *
 * Each turn produces two messages: a User message followed by
 * an Assistant message, preserving chronological order.
 */
internal fun turnsToMessages(turns: List<Turn>): List<ContextMessage> =
    turns.flatMap {
        listOf(
            ContextMessage(role = MessageRole.User, content = it.userMessage),
            ContextMessage(role = MessageRole.Assistant, content = it.assistantMessage),
        )
    }
