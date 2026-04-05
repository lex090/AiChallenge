package com.ai.challenge.app.di

import com.ai.challenge.agent.AiAgent
import com.ai.challenge.compressor.LlmContextCompressor
import com.ai.challenge.context.DefaultContextManager
import com.ai.challenge.context.TurnCountStrategy
import com.ai.challenge.core.Agent
import com.ai.challenge.core.CompressionStrategy
import com.ai.challenge.core.ContextCompressor
import com.ai.challenge.core.ContextManager
import com.ai.challenge.core.CostRepository
import com.ai.challenge.core.SessionRepository
import com.ai.challenge.core.SummaryRepository
import com.ai.challenge.core.TokenRepository
import com.ai.challenge.core.TurnRepository
import com.ai.challenge.cost.repository.ExposedCostRepository
import com.ai.challenge.cost.repository.createCostDatabase
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.session.repository.ExposedSessionRepository
import com.ai.challenge.session.repository.createSessionDatabase
import com.ai.challenge.summary.repository.ExposedSummaryRepository
import com.ai.challenge.summary.repository.createSummaryDatabase
import com.ai.challenge.token.repository.ExposedTokenRepository
import com.ai.challenge.token.repository.createTokenDatabase
import com.ai.challenge.turn.repository.ExposedTurnRepository
import com.ai.challenge.turn.repository.createTurnDatabase
import org.koin.dsl.module

val appModule = module {
    single {
        OpenRouterService(
            apiKey = System.getenv("OPENROUTER_API_KEY")
                ?: error("OPENROUTER_API_KEY environment variable is not set"),
        )
    }
    single<SessionRepository> { ExposedSessionRepository(createSessionDatabase()) }
    single<TurnRepository> { ExposedTurnRepository(createTurnDatabase()) }
    single<TokenRepository> { ExposedTokenRepository(createTokenDatabase()) }
    single<CostRepository> { ExposedCostRepository(createCostDatabase()) }
    single<SummaryRepository> { ExposedSummaryRepository(createSummaryDatabase()) }
    single<CompressionStrategy> { TurnCountStrategy(maxTurns = 15, retainLast = 5) }
    single<ContextCompressor> { LlmContextCompressor(service = get(), model = "google/gemini-2.0-flash-001") }
    single<ContextManager> { DefaultContextManager(strategy = get(), compressor = get(), summaryRepository = get()) }
    single<Agent> {
        AiAgent(
            service = get(),
            model = "google/gemini-2.0-flash-001",
            sessionRepository = get(),
            turnRepository = get(),
            tokenRepository = get(),
            costRepository = get(),
            contextManager = get(),
        )
    }
}
