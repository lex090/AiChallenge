package com.ai.challenge.core

interface CompressionStrategy {
    fun shouldCompress(history: List<Turn>, lastCompressedIndex: Int?): Boolean
    fun partitionPoint(history: List<Turn>): Int
}
