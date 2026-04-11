package com.ai.challenge.app.di

import com.ai.challenge.agent.AiBranchService
import com.ai.challenge.agent.AiChatService
import com.ai.challenge.agent.AiSessionService
import com.ai.challenge.agent.AiUsageService
import com.ai.challenge.context.BranchingContextManager
import com.ai.challenge.context.ContextCompressor
import com.ai.challenge.context.DefaultContextManager
import com.ai.challenge.context.FactExtractor
import com.ai.challenge.context.LlmContextCompressor
import com.ai.challenge.context.LlmFactExtractor
import com.ai.challenge.core.chat.BranchService
import com.ai.challenge.core.chat.ChatService
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.fact.FactRepository
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.llm.LlmPort
import com.ai.challenge.core.summary.SummaryRepository
import com.ai.challenge.core.event.DomainEvent
import com.ai.challenge.core.event.DomainEventPublisher
import com.ai.challenge.core.usage.UsageService
import com.ai.challenge.app.event.InProcessDomainEventPublisher
import com.ai.challenge.context.SessionDeletedCleanupHandler
import com.ai.challenge.fact.repository.ExposedFactRepository
import com.ai.challenge.fact.repository.createFactDatabase
import com.ai.challenge.llm.OpenRouterAdapter
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.session.repository.ExposedAgentSessionRepository
import com.ai.challenge.session.repository.createSessionDatabase
import com.ai.challenge.summary.repository.ExposedSummaryRepository
import com.ai.challenge.summary.repository.createSummaryDatabase
import org.koin.dsl.module

val appModule = module {
    single {
        OpenRouterService(
            apiKey = System.getenv("OPENROUTER_API_KEY")
                ?: error("OPENROUTER_API_KEY environment variable is not set"),
        )
    }

    single<LlmPort> {
        OpenRouterAdapter(
            openRouterService = get(),
            model = "google/gemini-2.0-flash-001",
        )
    }

    // Repositories (3 instead of 9)
    single<AgentSessionRepository> { ExposedAgentSessionRepository(database = createSessionDatabase()) }
    single<FactRepository> { ExposedFactRepository(database = createFactDatabase()) }
    single<SummaryRepository> { ExposedSummaryRepository(database = createSummaryDatabase()) }

    // Context management
    single<ContextCompressor> { LlmContextCompressor(llmPort = get()) }
    single<FactExtractor> { LlmFactExtractor(llmPort = get()) }
    single { BranchingContextManager(repository = get()) }
    single<ContextManager> {
        DefaultContextManager(
            repository = get(),
            compressor = get(),
            summaryRepository = get(),
            factExtractor = get(),
            factRepository = get(),
            branchingContextManager = get(),
        )
    }

    // Domain services (4 instead of 1 god object)
    single<ChatService> { AiChatService(llmPort = get(), repository = get(), contextManager = get()) }
    single<SessionService> { AiSessionService(repository = get()) }
    single<BranchService> { AiBranchService(repository = get()) }
    single<UsageService> { AiUsageService(repository = get()) }

    // Domain Events
    single {
        SessionDeletedCleanupHandler(
            factRepository = get(),
            summaryRepository = get(),
        )
    }

    single<DomainEventPublisher> {
        InProcessDomainEventPublisher(
            handlers = mapOf(
                DomainEvent.SessionDeleted::class to listOf(get<SessionDeletedCleanupHandler>()),
            ),
        )
    }
}
