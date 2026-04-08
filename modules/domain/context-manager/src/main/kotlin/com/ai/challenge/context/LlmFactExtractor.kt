package com.ai.challenge.context

import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.fact.FactId
import com.ai.challenge.llm.OpenRouterService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LlmFactExtractor(
    private val service: OpenRouterService,
    private val model: String,
) : FactExtractor {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun extract(
        currentFacts: List<Fact>,
        newUserMessage: String,
        lastAssistantResponse: String?,
    ): List<Fact> {
        val responseText = service.chatText(model = model) {
            jsonMode = true
            system(SYSTEM_PROMPT)
            if (currentFacts.isNotEmpty()) {
                user("Current facts:\n${formatFactsAsJson(facts = currentFacts)}")
            }
            if (lastAssistantResponse != null) {
                assistant(lastAssistantResponse)
            }
            user(newUserMessage)
            user("Extract and return the updated facts as a JSON array.")
        }
        return parseFacts(responseText = responseText, fallback = currentFacts)
    }

    private fun formatFactsAsJson(facts: List<Fact>): String {
        val entries = facts.joinToString(",\n  ") { fact ->
            """{"category":"${fact.category.name}","key":"${fact.key}","value":"${fact.value}"}"""
        }
        return "[\n  $entries\n]"
    }

    private fun parseFacts(responseText: String, fallback: List<Fact>): List<Fact> =
        try {
            json.parseToJsonElement(responseText).jsonArray.map { element ->
                val obj = element.jsonObject
                Fact(
                    id = FactId.generate(),
                    category = parseCategory(category = obj["category"]!!.jsonPrimitive.content),
                    key = obj["key"]!!.jsonPrimitive.content,
                    value = obj["value"]!!.jsonPrimitive.content,
                )
            }
        } catch (_: Exception) {
            fallback
        }

    private fun parseCategory(category: String): FactCategory = when (category) {
        "Goal" -> FactCategory.Goal
        "Constraint" -> FactCategory.Constraint
        "Preference" -> FactCategory.Preference
        "Decision" -> FactCategory.Decision
        "Agreement" -> FactCategory.Agreement
        else -> FactCategory.Goal
    }

    companion object {
        private val SYSTEM_PROMPT = """
            You extract and maintain structured facts from a conversation.

            Categories:
            - Goal: the user's objectives
            - Constraint: limitations and requirements
            - Preference: user preferences
            - Decision: decisions that have been made
            - Agreement: agreements between user and assistant

            Return a JSON array of objects with fields: "category", "key", "value".
            Each fact has a short descriptive key and a concise value.
            If previous facts are provided, update them: add new facts, modify changed ones, remove obsolete ones.
            Return ONLY the JSON array, no other text.
        """.trimIndent()
    }
}
