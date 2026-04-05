package com.ai.challenge.fact.extractor

import com.ai.challenge.core.Fact
import com.ai.challenge.core.FactExtractor
import com.ai.challenge.core.Turn
import com.ai.challenge.llm.OpenRouterService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class LlmFactExtractor(
    private val service: OpenRouterService,
    private val model: String,
) : FactExtractor {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun extract(
        history: List<Turn>,
        currentFacts: List<Fact>,
        newMessage: String,
    ): List<Fact> {
        val responseText = service.chatText(model) {
            system(buildPrompt(currentFacts))
            for (turn in history.takeLast(3)) {
                user(turn.userMessage)
                assistant(turn.agentResponse)
            }
            user(newMessage)
            user("Extract all key facts from this conversation. Return a JSON array of strings, e.g. [\"fact1\", \"fact2\"]. Only return the JSON array, nothing else.")
        }

        return parseFacts(responseText, currentFacts)
    }

    private fun buildPrompt(currentFacts: List<Fact>): String = buildString {
        appendLine("You are a fact extractor. Analyze the conversation and extract key facts (user preferences, decisions, important context).")
        appendLine("Return ONLY a JSON array of fact strings.")
        if (currentFacts.isNotEmpty()) {
            appendLine("Existing facts to consider (merge, update, or keep as needed):")
            for (fact in currentFacts) {
                appendLine("- ${fact.content}")
            }
        }
    }

    private fun parseFacts(responseText: String, currentFacts: List<Fact>): List<Fact> {
        return try {
            val trimmed = responseText.trim()
            val jsonText = if (trimmed.startsWith("[")) {
                trimmed
            } else {
                val start = trimmed.indexOf('[')
                val end = trimmed.lastIndexOf(']')
                if (start >= 0 && end > start) trimmed.substring(start, end + 1) else return currentFacts
            }
            val array = json.parseToJsonElement(jsonText).jsonArray
            array.map { element ->
                Fact(content = element.jsonPrimitive.content)
            }
        } catch (_: Exception) {
            currentFacts
        }
    }
}
