package com.ai.challenge

suspend fun main() {
    val apiKey = System.getenv("OPENROUTER_API_KEY") ?: error("Set OPENROUTER_API_KEY env variable")

    val model = "google/gemini-3.1-flash-lite-preview"

    val service = OpenRouterService(
        apiKey = apiKey,
        defaultModel = model,
    )

    service.use {
        val answer = it.chat()
            .system("You are a helpful polyglot assistant.")
            .user("Say hello in three languages")
            .execute()

        println(answer)
    }
}
