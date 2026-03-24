package com.ai.challenge

suspend fun main() {
    val apiKey = System.getenv("OPENROUTER_API_KEY") ?: error("Set OPENROUTER_API_KEY env variable")
    val service = OpenRouterService(apiKey = apiKey, defaultModel = "google/gemini-3.1-flash-lite-preview")

    service.use {
        demoResponseFormat(it)
        println()
        demoMaxTokens(it)
        println()
        demoStop(it)
    }
}

suspend fun demoResponseFormat(service: OpenRouterService) {
    println("=== response_format ===")

    println("\n--- БЕЗ jsonMode ---")
    val noJson = service.chat()
        .user("Return a JSON object with fields: name, age, city. For a person named Alice, age 30, from Paris.")
        .executeText()
    println(noJson)

    println("\n--- С jsonMode ---")
    val withJson = service.chat()
        .system("Always respond with valid JSON only.")
        .user("Return a JSON object with fields: name, age, city. For a person named Alice, age 30, from Paris.")
        .jsonMode()
        .executeText()
    println(withJson)
}

suspend fun demoMaxTokens(service: OpenRouterService) {
    println("=== max_tokens ===")

    println("\n--- БЕЗ maxTokens ---")
    val noLimit = service.chat()
        .user("List 10 programming languages and briefly describe each one.")
        .executeText()
    println(noLimit)

    println("\n--- С maxTokens=50 ---")
    val withLimit = service.chat()
        .user("List 10 programming languages and briefly describe each one.")
        .maxTokens(50)
        .execute()
    println(withLimit.choices.first().message.content)
    println("  >> finishReason: ${withLimit.choices.first().finishReason}")
}

suspend fun demoStop(service: OpenRouterService) {
    println("=== stop ===")

    println("\n--- БЕЗ stop ---")
    val noStop = service.chat()
        .user("Count from 1 to 10, each number on a new line.")
        .executeText()
    println(noStop)

    println("\n--- С stop(\"5\") ---")
    val withStop = service.chat()
        .user("Count from 1 to 10, each number on a new line.")
        .stop("5")
        .executeText()
    println(withStop)
}
