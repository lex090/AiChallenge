package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.llm.model.ChatResponse
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.CostDetails
import com.ai.challenge.session.RequestMetrics
import com.ai.challenge.session.SessionId
import com.ai.challenge.session.TokenDetails
import com.ai.challenge.session.Turn
import com.ai.challenge.session.UsageManager

class OpenRouterAgent(
    private val service: OpenRouterService,
    private val model: String,
    private val sessionManager: AgentSessionManager,
    private val usageManager: UsageManager,
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

        val metrics = chatResponse.toRequestMetrics()
        val turn = Turn(userMessage = message, agentResponse = text)
        val turnId = sessionManager.appendTurn(sessionId, turn)
        usageManager.record(turnId, metrics)

        AgentResponse(text = text, turnId = turnId, metrics = metrics)
    }
}

private fun ChatResponse.toRequestMetrics(): RequestMetrics = RequestMetrics(
    tokens = TokenDetails(
        promptTokens = usage?.promptTokens ?: 0,
        completionTokens = usage?.completionTokens ?: 0,
        cachedTokens = usage?.promptTokensDetails?.cachedTokens ?: 0,
        cacheWriteTokens = usage?.promptTokensDetails?.cacheWriteTokens ?: 0,
        reasoningTokens = usage?.completionTokensDetails?.reasoningTokens ?: 0,
    ),
    cost = CostDetails(
        totalCost = usage?.cost ?: cost ?: 0.0,
        upstreamCost = usage?.costDetails?.upstreamCost ?: costDetails?.upstreamCost ?: 0.0,
        upstreamPromptCost = usage?.costDetails?.upstreamPromptCost ?: costDetails?.upstreamPromptCost ?: 0.0,
        upstreamCompletionsCost = usage?.costDetails?.upstreamCompletionsCost ?: costDetails?.upstreamCompletionsCost ?: 0.0,
    ),
)
