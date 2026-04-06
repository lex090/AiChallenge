package com.ai.challenge.app.di

import com.ai.challenge.agent.AiAgent
import com.ai.challenge.compressor.LlmContextCompressor
import com.ai.challenge.context.ContextStrategyFactory
import com.ai.challenge.context.DefaultContextManager
import com.ai.challenge.context.repository.ExposedContextManagementTypeRepository
import com.ai.challenge.context.repository.createContextManagementDatabase
import com.ai.challenge.core.agent.Agent
import com.ai.challenge.core.context.ContextCompressor
import com.ai.challenge.core.context.ContextManagementTypeRepository
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.cost.CostDetailsRepository
import com.ai.challenge.core.session.AgentSessionRepository
import com.ai.challenge.core.summary.SummaryRepository
import com.ai.challenge.core.token.TokenDetailsRepository
import com.ai.challenge.core.turn.TurnRepository
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
    single<AgentSessionRepository> { ExposedSessionRepository(createSessionDatabase()) }
    single<TurnRepository> { ExposedTurnRepository(createTurnDatabase()) }
    single<TokenDetailsRepository> { ExposedTokenRepository(createTokenDatabase()) }
    single<CostDetailsRepository> { ExposedCostRepository(createCostDatabase()) }
    single<SummaryRepository> { ExposedSummaryRepository(createSummaryDatabase()) }
    single<ContextManagementTypeRepository> { ExposedContextManagementTypeRepository(createContextManagementDatabase()) }
    single { ContextStrategyFactory() }
    single<ContextCompressor> { LlmContextCompressor(service = get(), model = "google/gemini-2.0-flash-001") }
    single<ContextManager> {
        DefaultContextManager(
            contextManagementRepository = get(),
            strategyFactory = get(),
            compressor = get(),
            summaryRepository = get(),
        )
    }
    single<Agent> {
        AiAgent(
            service = get(),
            model = "google/gemini-2.0-flash-001",
            sessionRepository = get(),
            turnRepository = get(),
            tokenRepository = get(),
            costRepository = get(),
            contextManager = get(),
            contextManagementRepository = get(),
        )
    }
}
