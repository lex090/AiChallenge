package com.ai.challenge.context

import arrow.core.getOrElse
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.context.ContextMessage
import com.ai.challenge.core.context.MessageRole
import com.ai.challenge.core.context.model.FactKey
import com.ai.challenge.core.context.model.FactValue
import com.ai.challenge.core.fact.Fact
import com.ai.challenge.core.fact.FactCategory
import com.ai.challenge.core.llm.LlmPort
import com.ai.challenge.core.llm.ResponseFormat
import com.ai.challenge.core.session.AgentSessionId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LlmFactExtractor(
    private val llmPort: LlmPort,
) : FactExtractor {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun extract(
        sessionId: AgentSessionId,
        currentFacts: List<Fact>,
        newUserMessage: MessageContent,
        lastAssistantResponse: MessageContent?,
    ): List<Fact> {
        val messages = buildList {
            add(ContextMessage(role = MessageRole.System, content = MessageContent(value = SYSTEM_PROMPT)))
            if (currentFacts.isNotEmpty()) {
                add(ContextMessage(role = MessageRole.User, content = MessageContent(value = "Current facts:\n${formatFactsAsJson(facts = currentFacts)}")))
            }
            if (lastAssistantResponse != null) {
                add(ContextMessage(role = MessageRole.Assistant, content = lastAssistantResponse))
            }
            add(ContextMessage(role = MessageRole.User, content = newUserMessage))
            add(ContextMessage(role = MessageRole.User, content = MessageContent(value = "Extract and return the updated facts as a JSON array.")))
        }

        val response = llmPort.complete(messages = messages, responseFormat = ResponseFormat.Json)
            .getOrElse { return currentFacts }
        return parseFacts(sessionId = sessionId, responseText = response.content.value, fallback = currentFacts)
    }

    private fun formatFactsAsJson(facts: List<Fact>): String {
        val entries = facts.joinToString(",\n  ") { fact ->
            """{"category":"${fact.category.name}","key":"${fact.key.value}","value":"${fact.value.value}"}"""
        }
        return "[\n  $entries\n]"
    }

    private fun parseFacts(sessionId: AgentSessionId, responseText: String, fallback: List<Fact>): List<Fact> =
        try {
            json.parseToJsonElement(responseText).jsonArray.map { element ->
                val obj = element.jsonObject
                Fact(
                    sessionId = sessionId,
                    category = parseCategory(category = obj["category"]!!.jsonPrimitive.content),
                    key = FactKey(value = obj["key"]!!.jsonPrimitive.content),
                    value = FactValue(value = obj["value"]!!.jsonPrimitive.content),
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
