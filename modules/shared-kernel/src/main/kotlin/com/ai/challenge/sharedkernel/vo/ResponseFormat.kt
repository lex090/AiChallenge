package com.ai.challenge.sharedkernel.vo

/**
 * Value Object -- desired response format for LLM completions.
 *
 * [Text] for free-form responses, [Json] when structured
 * JSON output is required (e.g., fact extraction).
 */
sealed interface ResponseFormat {
    data object Text : ResponseFormat
    data object Json : ResponseFormat
}
