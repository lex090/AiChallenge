package com.ai.challenge.core.fact

data class Fact(
    val id: FactId,
    val category: FactCategory,
    val key: String,
    val value: String,
)
