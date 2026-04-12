package com.ai.challenge.infrastructure.llm

import com.ai.challenge.infrastructure.llm.model.ChatRequest
import com.ai.challenge.infrastructure.llm.model.Message
import com.ai.challenge.infrastructure.llm.model.ResponseFormat

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class OpenRouterDsl

@OpenRouterDsl
class ChatScope(private val model: String) {
    private val messages = mutableListOf<Message>()

    var temperature: Double? = null
    var maxTokens: Int? = null
    var topP: Double? = null
    var frequencyPenalty: Double? = null
    var presencePenalty: Double? = null
    var stop: List<String>? = null
    var jsonMode: Boolean = false

    fun system(content: String) {
        messages.add(Message(role = "system", content = content))
    }

    fun user(content: String) {
        messages.add(Message(role = "user", content = content))
    }

    fun assistant(content: String) {
        messages.add(Message(role = "assistant", content = content))
    }

    fun message(role: String, content: String) {
        messages.add(Message(role = role, content = content))
    }

    fun stop(vararg values: String) {
        stop = values.toList()
    }

    fun build(): ChatRequest = ChatRequest(
        model = model,
        messages = messages.toList(),
        temperature = temperature,
        maxTokens = maxTokens,
        topP = topP,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty,
        stop = stop,
        responseFormat = if (jsonMode) ResponseFormat(type = "json_object") else null,
    )
}
