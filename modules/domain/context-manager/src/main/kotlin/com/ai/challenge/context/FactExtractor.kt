package com.ai.challenge.context

import com.ai.challenge.core.fact.Fact

interface FactExtractor {
    suspend fun extract(
        currentFacts: List<Fact>,
        newUserMessage: String,
        lastAssistantResponse: String?,
    ): List<Fact>
}
