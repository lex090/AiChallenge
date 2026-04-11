package com.ai.challenge.context

import com.ai.challenge.core.context.model.SummaryContent
import com.ai.challenge.core.summary.Summary
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.llm.OpenRouterService

class LlmContextCompressor(
    private val service: OpenRouterService,
    private val model: String,
) : ContextCompressor {

    override suspend fun compress(turns: List<Turn>, previousSummary: Summary?): SummaryContent {
        val text = service.chatText(model) {
            if (previousSummary != null) {
                system("You have a previous conversation summary (covering messages 1-${previousSummary.toTurnIndex.value}) and new messages. Create an updated summary that incorporates both, preserving key facts, decisions, and context needed for continuation.")
                user("Previous summary:\n${previousSummary.content.value}")
            } else {
                system("Summarize the following conversation concisely, preserving key facts, decisions, and context needed for continuation.")
            }
            for (turn in turns) {
                user(turn.userMessage.value)
                assistant(turn.assistantMessage.value)
            }
            user("Provide a concise summary of the conversation above.")
        }
        return SummaryContent(value = text)
    }
}
