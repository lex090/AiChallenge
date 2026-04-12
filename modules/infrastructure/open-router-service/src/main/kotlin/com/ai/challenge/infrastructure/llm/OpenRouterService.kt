package com.ai.challenge.infrastructure.llm

import com.ai.challenge.infrastructure.llm.model.ChatRequest
import com.ai.challenge.infrastructure.llm.model.ChatResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Infrastructure service -- HTTP client for OpenRouter LLM API.
 *
 * Encapsulates HTTP transport, serialization, and request logging.
 * Provides both raw [send] and DSL-based [chat]/[chatText] methods.
 * Used by [OpenRouterAdapter] to fulfill the [com.ai.challenge.sharedkernel.port.LlmPort] contract.
 */
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

        private val prettyJson = Json { prettyPrint = true }

        private fun formatJsonInMessage(message: String): String =
            try {
                val lines = message.lines()
                val bodyStartIndex = lines.indexOfLast { it == "BODY START" }
                val bodyEndIndex = lines.lastIndexOf("BODY END")
                if (bodyStartIndex >= 0 && bodyEndIndex > bodyStartIndex) {
                    val bodyText = lines.subList(bodyStartIndex + 1, bodyEndIndex).joinToString(separator = "\n").trim()
                    if (bodyText.startsWith("{") || bodyText.startsWith("[")) {
                        val element = prettyJson.decodeFromString<JsonElement>(bodyText)
                        val formatted = prettyJson.encodeToString(serializer = JsonElement.serializer(), value = element)
                        val before = lines.subList(0, bodyStartIndex + 1).joinToString(separator = "\n")
                        val after = lines.subList(bodyEndIndex, lines.size).joinToString(separator = "\n")
                        "$before\n$formatted\n$after"
                    } else {
                        message
                    }
                } else {
                    message
                }
            } catch (_: Exception) {
                message
            }

        fun createDefaultClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(json)
            }
            install(Logging) {
                level = LogLevel.BODY
                logger = object : Logger {
                    private val slf4j = org.slf4j.LoggerFactory.getLogger("HttpClient")
                    override fun log(message: String) {
                        slf4j.info(formatJsonInMessage(message = message))
                    }
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
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
