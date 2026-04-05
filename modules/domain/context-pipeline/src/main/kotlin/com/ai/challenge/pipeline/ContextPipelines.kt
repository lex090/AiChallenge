package com.ai.challenge.pipeline

import com.ai.challenge.core.BranchRepository
import com.ai.challenge.core.ContextPipeline
import com.ai.challenge.core.FactExtractor
import com.ai.challenge.core.FactRepository
import com.ai.challenge.core.TurnRepository

object ContextPipelines {
    fun slidingWindow(windowSize: Int = 10): ContextPipeline = ContextPipeline(
        listOf(
            SlidingWindowMiddleware(windowSize),
            MessageBuilderMiddleware(),
        )
    )

    fun stickyFacts(
        factExtractor: FactExtractor,
        factRepository: FactRepository,
        windowSize: Int = 10,
    ): ContextPipeline = ContextPipeline(
        listOf(
            FactExtractionMiddleware(factExtractor, factRepository),
            SlidingWindowMiddleware(windowSize),
            MessageBuilderMiddleware(),
        )
    )

    fun branching(
        branchRepository: BranchRepository,
        turnRepository: TurnRepository,
        windowSize: Int = 10,
    ): ContextPipeline = ContextPipeline(
        listOf(
            BranchRoutingMiddleware(branchRepository, turnRepository),
            SlidingWindowMiddleware(windowSize),
            MessageBuilderMiddleware(),
        )
    )
}
