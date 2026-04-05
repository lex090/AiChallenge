package com.ai.challenge.core

interface FactExtractor {
    suspend fun extract(
        history: List<Turn>,
        currentFacts: List<Fact>,
        newMessage: String,
    ): List<Fact>
}
