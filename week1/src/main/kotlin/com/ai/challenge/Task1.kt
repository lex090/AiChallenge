package com.ai.challenge

import com.ai.challenge.llm.OpenRouterService

suspend fun main() {
    val apiKey = System.getenv("OPENROUTER_API_KEY") ?: error("Set OPENROUTER_API_KEY env variable")

    val service = OpenRouterService(
        apiKey = apiKey,
        defaultModel = "google/gemini-3.1-flash-lite-preview",
    )

    service.use {
        val answer = it.chatText {
            system("You are a helpful polyglot assistant.")
            user("Say hello in three languages")
        }

        println(answer)
    }
}
