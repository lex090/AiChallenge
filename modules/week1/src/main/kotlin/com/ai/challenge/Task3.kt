package com.ai.challenge

import com.ai.challenge.infrastructure.llm.OpenRouterService

private const val PROBLEM = """
Пять друзей — Алексей, Борис, Вера, Галина и Дмитрий — живут в пятиэтажном доме, каждый на своём этаже (с 1-го по 5-й).
Известно следующее:
1. Борис живёт выше Веры, но ниже Дмитрия.
2. Алексей живёт не на первом и не на пятом этаже.
3. Галина живёт ниже Веры.
4. Между этажами Алексея и Дмитрия ровно два этажа.

На каком этаже живёт каждый из друзей?
"""

suspend fun main() {
    val apiKey = System.getenv("OPENROUTER_API_KEY") ?: error("Set OPENROUTER_API_KEY env variable")
    val service = OpenRouterService(apiKey = apiKey, defaultModel = "google/gemini-3.1-flash-lite-preview")

    service.use {
        val results = mutableMapOf<String, String>()

        results["1. Direct"] = directAnswer(it)
        println()
        results["2. Step-by-step"] = stepByStep(it)
        println()
        results["3. Self-prompt"] = selfPrompt(it)
        println()
        results["4. Expert panel"] = expertPanel(it)
        println()
        compareResults(it, results)
    }
}

private suspend fun directAnswer(service: OpenRouterService): String {
    println("=== 1. Прямой ответ (без инструкций) ===")
    val response = service.chatText {
        user(PROBLEM)
    }
    println(response)
    return response
}

private suspend fun stepByStep(service: OpenRouterService): String {
    println("=== 2. Пошаговое решение ===")
    val response = service.chatText {
        system("Решай задачу пошагово, показывая рассуждения на каждом этапе. Отвечай на русском языке.")
        user(PROBLEM)
    }
    println(response)
    return response
}

private suspend fun selfPrompt(service: OpenRouterService): String {
    println("=== 3. Само-промпт ===")

    println("--- Шаг 1: Генерация промпта ---")
    val generatedPrompt = service.chatText {
        system("Ты эксперт по промпт-инжинирингу. Составь наилучший промпт для точного решения данной задачи. Верни ТОЛЬКО текст промпта, ничего больше. Отвечай на русском языке.")
        user("Составь оптимальный промпт для решения этой задачи:\n$PROBLEM")
    }
    println(generatedPrompt)

    println("\n--- Шаг 2: Решение с помощью сгенерированного промпта ---")
    val response = service.chatText {
        user(generatedPrompt)
    }
    println(response)
    return response
}

private suspend fun expertPanel(service: OpenRouterService): String {
    println("=== 4. Панель экспертов ===")
    val response = service.chatText {
        system("""
            Ты симулируешь панель из трёх экспертов, обсуждающих задачу.
            Каждый эксперт должен дать свой анализ, а затем они должны дополнять и оспаривать рассуждения друг друга.

            Эксперты:
            1. Математик (специалист по теории вероятностей и комбинаторике)
            2. Аналитик данных (подходит к задаче через моделирование и числовые оценки)
            3. Критик (ищет ошибки в рассуждениях, проверяет граничные случаи)

            Оформи ответ с чёткими заголовками для каждого эксперта.
            В конце дай консенсусный вывод. Отвечай на русском языке.
        """.trimIndent())
        user(PROBLEM)
    }
    println(response)
    return response
}

private suspend fun compareResults(service: OpenRouterService, results: Map<String, String>) {
    println("=== 5. Сравнение результатов ===")
    val allResults = results.entries.joinToString("\n\n") { (key, value) ->
        "=== $key ===\n$value"
    }
    val comparison = service.chatText {
        system("Ты аналитический судья. Сравни приведённые решения. Оцени: точность ответа, глубину рассуждений и ясность изложения. Определи, какой подход дал лучший результат и почему. Отвечай на русском языке.")
        user("Одна и та же задача по теории вероятностей была решена 4 разными способами промптинга. Сравни их:\n\n$allResults")
    }
    println(comparison)
}
