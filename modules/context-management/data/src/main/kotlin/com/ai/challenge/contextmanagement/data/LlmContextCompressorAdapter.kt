package com.ai.challenge.contextmanagement.data

import arrow.core.getOrElse
import com.ai.challenge.contextmanagement.model.Summary
import com.ai.challenge.contextmanagement.model.SummaryContent
import com.ai.challenge.contextmanagement.strategy.ContextCompressorPort
import com.ai.challenge.sharedkernel.port.LlmPort
import com.ai.challenge.sharedkernel.vo.ContextMessage
import com.ai.challenge.sharedkernel.vo.MessageContent
import com.ai.challenge.sharedkernel.vo.MessageRole
import com.ai.challenge.sharedkernel.vo.ResponseFormat
import com.ai.challenge.sharedkernel.vo.TurnSnapshot

/**
 * Adapter -- LLM-based implementation of [ContextCompressorPort].
 *
 * Compresses a list of [TurnSnapshot]s into a concise [SummaryContent]
 * by sending the conversation to the LLM with a summarization prompt.
 * If a [previousSummary] is provided, the prompt instructs the LLM
 * to incorporate the existing summary with the new turns.
 *
 * Falls back to "Summary unavailable" if the LLM call fails.
 */
class LlmContextCompressorAdapter(
    private val llmPort: LlmPort,
) : ContextCompressorPort {

    override suspend fun compress(turns: List<TurnSnapshot>, previousSummary: Summary?): SummaryContent {
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

        val response = llmPort.complete(messages = messages, responseFormat = ResponseFormat.Text)
            .getOrElse { return SummaryContent(value = "Summary unavailable") }
        return SummaryContent(value = response.content.value)
    }
}
