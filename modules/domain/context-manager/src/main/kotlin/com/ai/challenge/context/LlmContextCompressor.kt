package com.ai.challenge.context

import arrow.core.Either
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.context.model.SummaryContent
import com.ai.challenge.core.llm.LlmPort
import com.ai.challenge.core.llm.ResponseFormat
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.turn.Turn

class LlmContextCompressor(
    private val llmPort: LlmPort,
) : ContextCompressor {

    override suspend fun compress(turns: List<Turn>, previousSummary: Summary?): SummaryContent {
        val messages = buildList {
            if (previousSummary != null) {
                add(ContextMessage(
                    role = MessageRole.System,
                    content = MessageContent(value = "You have a previous conversation summary (covering messages 1-${previousSummary.toTurnIndex.value}) and new messages. Create an updated summary that incorporates both, preserving key facts, decisions, and context needed for continuation."),
                ))
                add(ContextMessage(
                    role = MessageRole.User,
                    content = MessageContent(value = "Previous summary:\n${previousSummary.content.value}"),
                ))
            } else {
                add(ContextMessage(
                    role = MessageRole.System,
                    content = MessageContent(value = "Summarize the following conversation concisely, preserving key facts, decisions, and context needed for continuation."),
                ))
            }
            for (turn in turns) {
                add(ContextMessage(role = MessageRole.User, content = turn.userMessage))
                add(ContextMessage(role = MessageRole.Assistant, content = turn.assistantMessage))
            }
            add(ContextMessage(
                role = MessageRole.User,
                content = MessageContent(value = "Provide a concise summary of the conversation above."),
            ))
        }

        val result = llmPort.complete(messages = messages, responseFormat = ResponseFormat.Text)
        return when (result) {
            is Either.Right -> SummaryContent(value = result.value.content.value)
            is Either.Left -> SummaryContent(value = "Summary unavailable")
        }
    }
}
