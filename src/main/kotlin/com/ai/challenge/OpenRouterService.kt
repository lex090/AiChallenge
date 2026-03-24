package com.ai.challenge

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Double? = null,
    @SerialName("presence_penalty") val presencePenalty: Double? = null,
    val stop: List<String>? = null,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
)

@Serializable
data class ResponseFormat(val type: String)

@Serializable
data class Message(val role: String, val content: String)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
    val model: String? = null,
    val usage: Usage? = null,
    val error: ErrorBody? = null,
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: Message,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

@Serializable
data class ErrorBody(val message: String? = null, val code: Int? = null)

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
        messages.add(Message("system", content))
    }

    fun user(content: String) {
        messages.add(Message("user", content))
    }

    fun assistant(content: String) {
        messages.add(Message("assistant", content))
    }

    fun message(role: String, content: String) {
        messages.add(Message(role, content))
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
        responseFormat = if (jsonMode) ResponseFormat("json_object") else null,
    )
}

class OpenRouterService(
    private val apiKey: String,
    private val defaultModel: String? = null,
    private val client: HttpClient = createDefaultClient(),
) : AutoCloseable {

    companion object {
        private const val BASE_URL = "https://openrouter.ai/api/v1"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        fun createDefaultClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    suspend fun send(request: ChatRequest): ChatResponse {
        val responseText = client.post("$BASE_URL/chat/completions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(request)
        }.bodyAsText()

        return json.decodeFromString<ChatResponse>(responseText)
    }

    suspend fun chat(model: String? = null, init: @OpenRouterDsl ChatScope.() -> Unit): ChatResponse {
        val resolvedModel = model ?: defaultModel ?: error("Model must be specified either in constructor or in chat()")
        val scope = ChatScope(resolvedModel)
        scope.init()
        return send(scope.build())
    }

    suspend fun chatText(model: String? = null, init: @OpenRouterDsl ChatScope.() -> Unit): String {
        val response = chat(model, init)
        if (response.error != null) {
            error("OpenRouter API error: ${response.error.message}")
        }
        return response.choices.firstOrNull()?.message?.content
            ?: error("Empty response from OpenRouter")
    }

    override fun close() {
        client.close()
    }
}
