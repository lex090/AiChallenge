package com.ai.challenge.core

interface ContextCompressor {
    suspend fun compress(turns: List<Turn>): String
}
