package com.ai.challenge.ui.di

import com.ai.challenge.agent.AiAgent
import com.ai.challenge.core.Agent
import com.ai.challenge.core.CostRepository
import com.ai.challenge.core.SessionRepository
import com.ai.challenge.core.TokenRepository
import com.ai.challenge.core.TurnRepository
import com.ai.challenge.cost.repository.ExposedCostRepository
import com.ai.challenge.cost.repository.createCostDatabase
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.session.repository.ExposedSessionRepository
import com.ai.challenge.session.repository.createSessionDatabase
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
    single<Agent> {
        AiAgent(
            service = get(),
            model = "google/gemini-2.0-flash-001",
            sessionRepository = get(),
            turnRepository = get(),
            tokenRepository = get(),
            costRepository = get(),
        )
    }
}
