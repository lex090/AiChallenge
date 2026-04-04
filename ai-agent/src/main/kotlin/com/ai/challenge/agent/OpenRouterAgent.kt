package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.SessionId
import com.ai.challenge.session.TokenUsage
import com.ai.challenge.session.Turn

class OpenRouterAgent(
    private val service: OpenRouterService,
    private val model: String,
    private val sessionManager: AgentSessionManager,
) : Agent {

    override suspend fun send(sessionId: SessionId, message: String): Either<AgentError, AgentResponse> = either {
        val history = sessionManager.getHistory(sessionId)

        val chatResponse = catch({
            service.chat(model = model) {
                for (turn in history) {
                    user(turn.userMessage)
                    assistant(turn.agentResponse)
                }
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

        val error = chatResponse.error
        if (error != null) {
            raise(AgentError.ApiError(error.message ?: "Unknown API error"))
        }

        val text = chatResponse.choices.firstOrNull()?.message?.content
            ?: raise(AgentError.ApiError("Empty response from OpenRouter"))

        val tokenUsage = chatResponse.usage?.let {
            TokenUsage(
                promptTokens = it.promptTokens,
                completionTokens = it.completionTokens,
                totalTokens = it.totalTokens,
            )
        } ?: TokenUsage()

        sessionManager.appendTurn(sessionId, Turn(userMessage = message, agentResponse = text, tokenUsage = tokenUsage))

        AgentResponse(text = text, tokenUsage = tokenUsage)
    }
}
