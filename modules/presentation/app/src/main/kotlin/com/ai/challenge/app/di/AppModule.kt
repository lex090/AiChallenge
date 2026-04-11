package com.ai.challenge.app.di

import com.ai.challenge.agent.AiAgent
import com.ai.challenge.branch.repository.ExposedBranchRepository
import com.ai.challenge.branch.repository.createBranchDatabase
import com.ai.challenge.context.BranchingContextManager
import com.ai.challenge.context.DefaultContextManager
import com.ai.challenge.context.FactExtractor
import com.ai.challenge.context.LlmContextCompressor
import com.ai.challenge.context.LlmFactExtractor
import com.ai.challenge.core.fact.FactRepository
import com.ai.challenge.fact.repository.ExposedFactRepository
import com.ai.challenge.fact.repository.createFactDatabase
import com.ai.challenge.context.repository.ExposedContextManagementTypeRepository
import com.ai.challenge.context.repository.createContextManagementDatabase
import com.ai.challenge.core.agent.Agent
import com.ai.challenge.core.agent.BranchManager
import com.ai.challenge.core.agent.ChatAgent
import com.ai.challenge.core.agent.SessionManager
import com.ai.challenge.core.agent.UsageTracker
import com.ai.challenge.context.ContextCompressor
import com.ai.challenge.core.branch.BranchRepository
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
    single<TokenDetailsRepository> { ExposedTokenRepository(database = createTokenDatabase(), turnRepository = get()) }
    single<CostDetailsRepository> { ExposedCostRepository(database = createCostDatabase(), turnRepository = get()) }
    single<SummaryRepository> { ExposedSummaryRepository(createSummaryDatabase()) }
    single<ContextManagementTypeRepository> { ExposedContextManagementTypeRepository(createContextManagementDatabase()) }
    single<ContextCompressor> { LlmContextCompressor(service = get(), model = "google/gemini-2.0-flash-001") }
    single<FactRepository> { ExposedFactRepository(database = createFactDatabase()) }
    single<FactExtractor> { LlmFactExtractor(service = get(), model = "google/gemini-2.0-flash-001") }
    single<BranchRepository> { ExposedBranchRepository(createBranchDatabase()) }
    single {
        BranchingContextManager(
            turnRepository = get(),
            branchRepository = get(),
        )
    }
    single<ContextManager> {
        DefaultContextManager(
            contextManagementRepository = get(),
            compressor = get(),
            summaryRepository = get(),
            turnRepository = get(),
            factExtractor = get(),
            factRepository = get(),
            branchingContextManager = get(),
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
            branchRepository = get(),
        )
    }
    single<ChatAgent> { get<Agent>() }
    single<SessionManager> { get<Agent>() }
    single<UsageTracker> { get<Agent>() }
    single<BranchManager> { get<Agent>() }
}
