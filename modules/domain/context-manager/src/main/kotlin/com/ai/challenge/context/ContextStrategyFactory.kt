package com.ai.challenge.context

import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextStrategy
import com.ai.challenge.core.context.CompressionContext
import com.ai.challenge.core.context.CompressionDecision

class ContextStrategyFactory {
    fun create(type: ContextManagementType): ContextStrategy =
        when (type) {
            is ContextManagementType.None -> NoneContextStrategy
            is ContextManagementType.SummarizeOnThreshold -> SummarizeOnThresholdStrategy(
                maxTurns = 15,
                retainLast = 5,
                compressionInterval = 10,
            )
        }
}

private object NoneContextStrategy : ContextStrategy {
    override fun evaluate(context: CompressionContext): CompressionDecision = CompressionDecision.Skip
}
