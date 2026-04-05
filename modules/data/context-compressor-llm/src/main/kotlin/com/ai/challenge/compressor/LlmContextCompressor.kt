package com.ai.challenge.compressor

import com.ai.challenge.core.ContextCompressor
import com.ai.challenge.core.Turn
import com.ai.challenge.llm.OpenRouterService

class LlmContextCompressor(
    private val service: OpenRouterService,
    private val model: String,
) : ContextCompressor {

    override suspend fun compress(turns: List<Turn>): String {
        return service.chatText(model) {
            system("Summarize the following conversation concisely, preserving key facts, decisions, and context needed for continuation.")
            for (turn in turns) {
                user(turn.userMessage)
                assistant(turn.agentResponse)
            }
            user("Provide a concise summary of the conversation above.")
        }
    }
}
