package com.ai.challenge.compressor

import com.ai.challenge.core.ContextCompressor
import com.ai.challenge.core.Summary
import com.ai.challenge.core.Turn
import com.ai.challenge.llm.OpenRouterService

class LlmContextCompressor(
    private val service: OpenRouterService,
    private val model: String,
) : ContextCompressor {

    override suspend fun compress(turns: List<Turn>, previousSummary: Summary?): String {
        return service.chatText(model) {
            if (previousSummary != null) {
                system("You have a previous conversation summary (covering messages 1-${previousSummary.toTurnIndex}) and new messages. Create an updated summary that incorporates both, preserving key facts, decisions, and context needed for continuation.")
                user("Previous summary:\n${previousSummary.text}")
            } else {
                system("Summarize the following conversation concisely, preserving key facts, decisions, and context needed for continuation.")
            }
            for (turn in turns) {
                user(turn.userMessage)
                assistant(turn.agentResponse)
            }
            user("Provide a concise summary of the conversation above.")
        }
    }
}
