package com.ai.challenge

private val TEMPERATURES = listOf(0.0, 0.7, 1.2)

private const val FACTUAL_PROMPT = "Объясни, почему небо голубое. Ответь кратко, в 3-5 предложениях."
private const val CREATIVE_PROMPT = "Напиши короткую историю (5-7 предложений) про робота, который научился мечтать."

suspend fun main() {
    val apiKey = System.getenv("OPENROUTER_API_KEY") ?: error("Set OPENROUTER_API_KEY env variable")
    val service = OpenRouterService(apiKey = apiKey, defaultModel = "google/gemini-3.1-flash-lite-preview")

    service.use {
        val factualResults = mutableMapOf<Double, String>()
        val creativeResults = mutableMapOf<Double, String>()

        println("╔══════════════════════════════════════════════════════╗")
        println("║   Задание 4: Сравнение температур (0, 0.7, 1.2)    ║")
        println("╚══════════════════════════════════════════════════════╝")

        // --- Фактический запрос ---
        println("\n▓▓▓ ФАКТИЧЕСКИЙ ЗАПРОС ▓▓▓")
        println("Промпт: \"$FACTUAL_PROMPT\"\n")

        for (temp in TEMPERATURES) {
            println("--- temperature = $temp ---")
            val response = it.chatText {
                temperature = temp
                user(FACTUAL_PROMPT)
            }
            factualResults[temp] = response
            println(response)
            println()
        }

        // --- Творческий запрос ---
        println("▓▓▓ ТВОРЧЕСКИЙ ЗАПРОС ▓▓▓")
        println("Промпт: \"$CREATIVE_PROMPT\"\n")

        for (temp in TEMPERATURES) {
            println("--- temperature = $temp ---")
            val response = it.chatText {
                temperature = temp
                user(CREATIVE_PROMPT)
            }
            creativeResults[temp] = response
            println(response)
            println()
        }

        // --- Анализ ---
        analyzeResults(it, factualResults, creativeResults)
    }
}

private suspend fun analyzeResults(
    service: OpenRouterService,
    factualResults: Map<Double, String>,
    creativeResults: Map<Double, String>,
) {
    println("▓▓▓ АНАЛИЗ РЕЗУЛЬТАТОВ ▓▓▓\n")

    val allResponses = buildString {
        appendLine("=== ФАКТИЧЕСКИЙ ЗАПРОС: \"$FACTUAL_PROMPT\" ===")
        for ((temp, response) in factualResults) {
            appendLine("\n[temperature = $temp]")
            appendLine(response)
        }
        appendLine("\n=== ТВОРЧЕСКИЙ ЗАПРОС: \"$CREATIVE_PROMPT\" ===")
        for ((temp, response) in creativeResults) {
            appendLine("\n[temperature = $temp]")
            appendLine(response)
        }
    }

    val analysis = service.chatText {
        system("""
            Ты аналитик, который сравнивает ответы языковой модели при разных значениях temperature.

            Тебе даны ответы на два типа запросов (фактический и творческий) при temperature = 0, 0.7 и 1.2.

            Проведи сравнительный анализ по следующим критериям:
            1. **Точность** — насколько ответ фактически корректен и по существу
            2. **Креативность** — оригинальность формулировок, метафор, идей
            3. **Разнообразие** — отличия между ответами при разных температурах

            Затем сформулируй выводы:
            - Для каких задач лучше подходит каждая настройка temperature
            - Какую temperature рекомендуешь по умолчанию и почему

            Оформи ответ структурированно с заголовками. Отвечай на русском языке.
        """.trimIndent())
        user(allResponses)
    }
    println(analysis)
}
