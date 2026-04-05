package com.ai.challenge.pipeline

import com.ai.challenge.core.ContextMessage
import com.ai.challenge.core.ContextMiddleware
import com.ai.challenge.core.ContextState
import com.ai.challenge.core.Fact
import com.ai.challenge.core.MessageRole

class MessageBuilderMiddleware : ContextMiddleware {
    override suspend fun process(
        state: ContextState,
        next: suspend (ContextState) -> ContextState,
    ): ContextState {
        val messages = state.history.flatMap { turn ->
            listOf(
                ContextMessage(MessageRole.User, turn.userMessage),
                ContextMessage(MessageRole.Assistant, turn.agentResponse),
            )
        }

        val allMessages = if (state.facts.isNotEmpty()) {
            val factsMsg = ContextMessage(MessageRole.System, formatFacts(state.facts))
            listOf(factsMsg) + messages
        } else {
            messages
        }

        val finalMessages = allMessages + ContextMessage(MessageRole.User, state.newMessage)
        return next(state.copy(messages = finalMessages))
    }

    private fun formatFacts(facts: List<Fact>): String = buildString {
        appendLine("Known facts about this conversation:")
        for (fact in facts) {
            appendLine("- ${fact.content}")
        }
    }
}
