package com.ai.challenge.fact.extractor

import com.ai.challenge.core.Fact
import com.ai.challenge.core.FactExtractor
import com.ai.challenge.core.Turn
import com.ai.challenge.llm.OpenRouterService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

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
        val currentFactsText = if (currentFacts.isNotEmpty()) {
            "\n\nCurrently known facts:\n" +
                currentFacts.joinToString("\n") { """  "${it.key}": "${it.value}"""" }
        } else {
            ""
        }

        val responseText = service.chatText(model) {
            system(buildSystemPrompt(currentFactsText))
            jsonMode = true
            for (turn in history.takeLast(5)) {
                user(turn.userMessage)
                assistant(turn.agentResponse)
            }
            user(newMessage)
            user("Extract key-value facts from the conversation above. Return JSON: {\"facts\": [{\"key\": \"...\", \"value\": \"...\"}]}")
        }

        return parseFacts(responseText)
    }

    private fun buildSystemPrompt(currentFactsText: String): String =
        """You are a fact extractor. Analyze the conversation and extract key-value facts.
            |Focus on: user goals, constraints, preferences, decisions, technical choices, names, and important context.
            |Update existing facts if they changed. Remove facts that are no longer relevant.
            |Return a JSON object with a "facts" array containing objects with "key" and "value" fields.
            |Keys should be short descriptive labels (e.g., "goal", "language", "framework", "deadline").
            |Values should be concise summaries.$currentFactsText""".trimMargin()

    private fun parseFacts(responseText: String): List<Fact> {
        return try {
            val response = json.decodeFromString<FactsResponse>(responseText)
            val now = Clock.System.now()
            response.facts.map { Fact(key = it.key, value = it.value, updatedAt = now) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Serializable
    private data class FactsResponse(val facts: List<FactEntry>)

    @Serializable
    private data class FactEntry(val key: String, val value: String)
}
