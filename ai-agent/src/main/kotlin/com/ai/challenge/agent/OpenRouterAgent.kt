package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.ai.challenge.llm.OpenRouterService

class OpenRouterAgent(
    private val service: OpenRouterService,
    private val model: String,
) : Agent {

    override suspend fun send(message: String): Either<AgentError, String> = either {
        catch({
            service.chatText(model = model) {
                user(message)
            }
        }) { e: Exception ->
            val msg = e.message ?: "Unknown error"
            if (msg.startsWith("OpenRouter API error:")) {
                raise(AgentError.ApiError(msg.removePrefix("OpenRouter API error: ")))
            } else {
                raise(AgentError.NetworkError(msg))
            }
        }
    }
}
