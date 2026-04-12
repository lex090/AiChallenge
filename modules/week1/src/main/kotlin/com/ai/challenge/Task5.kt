package com.ai.challenge

import com.ai.challenge.infrastructure.llm.OpenRouterService

data class ModelInfo(
    val id: String,
    val label: String,
    val tier: String,
    val inputPricePerMToken: Double,
    val outputPricePerMToken: Double,
)

data class BenchmarkResult(
    val model: ModelInfo,
    val responseText: String,
    val timeMs: Long,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val costUsd: Double,
)

private val MODELS = listOf(
    ModelInfo(
        id = "google/gemma-3-4b-it",
        label = "Google Gemma 3 4B",
        tier = "Слабая (4B параметров)",
        inputPricePerMToken = 0.07,
        outputPricePerMToken = 0.14,
    ),
    ModelInfo(
        id = "google/gemini-2.0-flash-001",
        label = "Google Gemini 2.0 Flash",
        tier = "Средняя",
        inputPricePerMToken = 0.1,
        outputPricePerMToken = 0.4,
    ),
    ModelInfo(
        id = "anthropic/claude-sonnet-4",
        label = "Anthropic Claude Sonnet 4",
        tier = "Сильная",
        inputPricePerMToken = 3.0,
        outputPricePerMToken = 15.0,
    ),
)

private const val TASK_PROMPT = """Реши логическую задачу шаг за шагом.

В офисе работают 4 программиста: Алиса, Боб, Вика и Грег.
- Каждый пишет на одном языке: Python, Java, Kotlin или Go.
- Алиса не пишет на Python и не пишет на Go.
- Боб пишет на Python.
- Вика не пишет на Java.
- Грег не пишет на Kotlin.

Определи, кто на каком языке пишет. Покажи рассуждения."""

suspend fun main() {
    val apiKey = System.getenv("OPENROUTER_API_KEY") ?: error("Set OPENROUTER_API_KEY env variable")
    val service = OpenRouterService(apiKey = apiKey)

    service.use {
        println("╔══════════════════════════════════════════════════════════════╗")
        println("║   Задание 5: Сравнение моделей разного уровня              ║")
        println("╚══════════════════════════════════════════════════════════════╝")
        println()
        println("Промпт: логическая задача про 4 программистов и языки")
        println("═".repeat(62))

        val results = mutableListOf<BenchmarkResult>()

        for (model in MODELS) {
            println("\n▓▓▓ ${model.tier}: ${model.label} ▓▓▓")
            println("Модель: ${model.id}\n")

            val startTime = System.currentTimeMillis()
            val response = it.chat(model = model.id) {
                user(TASK_PROMPT)
            }
            val elapsed = System.currentTimeMillis() - startTime

            val text = response.choices.firstOrNull()?.message?.content ?: "(пустой ответ)"
            val usage = response.usage
            val promptTokens = usage?.promptTokens ?: 0
            val completionTokens = usage?.completionTokens ?: 0
            val totalTokens = usage?.totalTokens ?: 0

            val cost = (promptTokens * model.inputPricePerMToken + completionTokens * model.outputPricePerMToken) / 1_000_000.0

            val result = BenchmarkResult(
                model = model,
                responseText = text,
                timeMs = elapsed,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = totalTokens,
                costUsd = cost,
            )
            results.add(result)

            println("Ответ:")
            println(text)
            println()
            println("⏱ Время: ${elapsed} мс | 🔢 Токены: $promptTokens вх. + $completionTokens вых. = $totalTokens | 💰 \$${String.format("%.6f", cost)}")
        }

        // --- Сводная таблица ---
        println("\n" + "═".repeat(62))
        println("▓▓▓ СВОДНАЯ ТАБЛИЦА ▓▓▓\n")

        val header = String.format("%-28s %10s %8s %8s %12s", "Модель", "Время(мс)", "Вход", "Выход", "Стоимость")
        println(header)
        println("─".repeat(70))
        for (r in results) {
            println(String.format("%-28s %10d %8d %8d %12s",
                r.model.label.take(28),
                r.timeMs,
                r.promptTokens,
                r.completionTokens,
                "\$${String.format("%.6f", r.costUsd)}"
            ))
        }

        // --- Анализ LLM ---
        println("\n" + "═".repeat(62))
        println("▓▓▓ АНАЛИЗ РЕЗУЛЬТАТОВ ▓▓▓\n")

        val analysisInput = buildString {
            for (r in results) {
                appendLine("=== ${r.model.tier}: ${r.model.label} (${r.model.id}) ===")
                appendLine("Время ответа: ${r.timeMs} мс")
                appendLine("Токены: ${r.promptTokens} вх. + ${r.completionTokens} вых. = ${r.totalTokens}")
                appendLine("Стоимость: \$${String.format("%.6f", r.costUsd)}")
                appendLine("Ответ:")
                appendLine(r.responseText)
                appendLine()
            }
        }

        val analysis = it.chatText(model = "google/gemini-2.0-flash-001") {
            system("""
                Ты аналитик, сравнивающий ответы языковых моделей разного уровня.

                Правильный ответ на задачу — задача имеет два корректных решения:
                Вариант 1: Боб — Python, Алиса — Java, Вика — Kotlin, Грег — Go
                Вариант 2: Боб — Python, Алиса — Kotlin, Вика — Go, Грег — Java

                Сравни ответы по критериям:
                1. **Правильность** — верно ли решена задача, найдены ли оба решения или хотя бы одно корректное
                2. **Качество рассуждений** — логичность, последовательность, ясность цепочки
                3. **Скорость** — время ответа
                4. **Ресурсоёмкость** — количество токенов и стоимость

                Дай краткий вывод: в каких случаях какую модель лучше использовать.

                Ссылки на модели:
                - Gemma 3 4B: https://huggingface.co/google/gemma-3-4b-it
                - Gemini 2.0 Flash: https://ai.google.dev/gemini-api/docs/models#gemini-2.0-flash
                - Claude Sonnet 4: https://docs.anthropic.com/en/docs/about-claude/models

                Оформи структурированно. Отвечай на русском.
            """.trimIndent())
            user(analysisInput)
        }
        println(analysis)
    }
}
