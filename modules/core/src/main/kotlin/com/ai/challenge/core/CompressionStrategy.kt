package com.ai.challenge.core

interface CompressionStrategy {
    fun shouldCompress(context: CompressionContext): Boolean
    fun partitionPoint(context: CompressionContext): Int
}
