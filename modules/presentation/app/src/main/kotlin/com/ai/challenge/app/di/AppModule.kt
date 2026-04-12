package com.ai.challenge.app.di

import com.ai.challenge.agent.AiBranchService
import com.ai.challenge.agent.AiChatService
import com.ai.challenge.agent.AiSessionService
import com.ai.challenge.agent.AiUsageQueryService
import com.ai.challenge.context.BranchingContextManager
import com.ai.challenge.context.ContextCompressor
import com.ai.challenge.context.ContextPreparationService
import com.ai.challenge.context.ContextStrategy
import com.ai.challenge.context.FactExtractor
import com.ai.challenge.context.LlmContextCompressor
import com.ai.challenge.context.LlmFactExtractor
import com.ai.challenge.context.PassthroughStrategy
import com.ai.challenge.context.SlidingWindowStrategy
import com.ai.challenge.context.StickyFactsStrategy
import com.ai.challenge.context.SummarizeOnThresholdStrategy
import com.ai.challenge.core.chat.BranchService
import com.ai.challenge.core.chat.ChatService
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.context.ContextManager
import com.ai.challenge.core.context.ContextStrategyConfig
import com.ai.challenge.core.chat.AgentSessionRepository
import com.ai.challenge.core.fact.FactRepository
import com.ai.challenge.core.llm.LlmPort
import com.ai.challenge.core.memory.FactMemoryProvider
import com.ai.challenge.core.memory.MemoryService
import com.ai.challenge.core.memory.SummaryMemoryProvider
import com.ai.challenge.core.memory.usecase.AddSummaryUseCase
import com.ai.challenge.core.memory.usecase.DeleteSummaryUseCase
import com.ai.challenge.core.memory.usecase.GetMemoryUseCase
import com.ai.challenge.core.memory.usecase.UpdateFactsUseCase
import com.ai.challenge.core.summary.SummaryRepository
import com.ai.challenge.core.event.DomainEvent
import com.ai.challenge.core.event.DomainEventPublisher
import com.ai.challenge.core.usage.UsageQueryService
import com.ai.challenge.core.usecase.ApplicationInitService
import com.ai.challenge.core.usecase.CreateSessionUseCase
import com.ai.challenge.core.usecase.DeleteSessionUseCase
import com.ai.challenge.core.usecase.SendMessageUseCase
import com.ai.challenge.app.event.InProcessDomainEventPublisher
import com.ai.challenge.llm.OpenRouterAdapter
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.memory.repository.ExposedFactRepository
import com.ai.challenge.memory.repository.ExposedSummaryRepository
import com.ai.challenge.memory.repository.createMemoryDatabase
import com.ai.challenge.memory.service.DefaultMemoryService
import com.ai.challenge.memory.service.handler.SessionDeletedCleanupHandler
import com.ai.challenge.memory.service.provider.DefaultFactMemoryProvider
import com.ai.challenge.memory.service.provider.DefaultSummaryMemoryProvider
import com.ai.challenge.memory.service.usecase.DefaultAddSummaryUseCase
import com.ai.challenge.memory.service.usecase.DefaultDeleteSummaryUseCase
import com.ai.challenge.memory.service.usecase.DefaultGetMemoryUseCase
import com.ai.challenge.memory.service.usecase.DefaultUpdateFactsUseCase
import com.ai.challenge.session.repository.ExposedAgentSessionRepository
import com.ai.challenge.session.repository.createSessionDatabase
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

    // Repositories
    single<AgentSessionRepository> { ExposedAgentSessionRepository(database = createSessionDatabase()) }

    // Memory Layer
    single { createMemoryDatabase() }
    single<FactRepository> { ExposedFactRepository(database = get()) }
    single<SummaryRepository> { ExposedSummaryRepository(database = get()) }
    single<FactMemoryProvider> { DefaultFactMemoryProvider(factRepository = get()) }
    single<SummaryMemoryProvider> { DefaultSummaryMemoryProvider(summaryRepository = get()) }
    single<MemoryService> { DefaultMemoryService(factMemoryProvider = get(), summaryMemoryProvider = get()) }

    // Memory Use Cases
    single<GetMemoryUseCase> { DefaultGetMemoryUseCase(memoryService = get()) }
    single<UpdateFactsUseCase> { DefaultUpdateFactsUseCase(memoryService = get()) }
    single<AddSummaryUseCase> { DefaultAddSummaryUseCase(memoryService = get()) }
    single<DeleteSummaryUseCase> { DefaultDeleteSummaryUseCase(memoryService = get()) }

    // Context management
    single<ContextCompressor> { LlmContextCompressor(llmPort = get()) }
    single<FactExtractor> { LlmFactExtractor(llmPort = get()) }

    // Context strategies
    single { PassthroughStrategy(repository = get()) }
    single { SlidingWindowStrategy(repository = get()) }
    single {
        SummarizeOnThresholdStrategy(
            repository = get(),
            compressor = get(),
            memoryService = get(),
        )
    }
    single {
        StickyFactsStrategy(
            repository = get(),
            memoryService = get(),
            factExtractor = get(),
        )
    }
    single { BranchingContextManager(repository = get()) }

    single<ContextManager> {
        ContextPreparationService(
            strategies = mapOf(
                ContextManagementType.None to get<PassthroughStrategy>() as ContextStrategy,
                ContextManagementType.SummarizeOnThreshold to get<SummarizeOnThresholdStrategy>() as ContextStrategy,
                ContextManagementType.SlidingWindow to get<SlidingWindowStrategy>() as ContextStrategy,
                ContextManagementType.StickyFacts to get<StickyFactsStrategy>() as ContextStrategy,
                ContextManagementType.Branching to get<BranchingContextManager>() as ContextStrategy,
            ),
            configs = mapOf(
                ContextManagementType.None to ContextStrategyConfig.None as ContextStrategyConfig,
                ContextManagementType.SummarizeOnThreshold to ContextStrategyConfig.SummarizeOnThreshold(
                    maxTurnsBeforeCompression = 15,
                    retainLastTurns = 5,
                    compressionInterval = 10,
                ) as ContextStrategyConfig,
                ContextManagementType.SlidingWindow to ContextStrategyConfig.SlidingWindow(
                    windowSize = 10,
                ) as ContextStrategyConfig,
                ContextManagementType.StickyFacts to ContextStrategyConfig.StickyFacts(
                    retainLastTurns = 5,
                ) as ContextStrategyConfig,
                ContextManagementType.Branching to ContextStrategyConfig.Branching as ContextStrategyConfig,
            ),
            repository = get(),
        )
    }

    // Domain services
    single<ChatService> { AiChatService(llmPort = get(), repository = get(), contextManager = get()) }
    single<SessionService> { AiSessionService(repository = get()) }
    single<BranchService> { AiBranchService(repository = get()) }
    single<UsageQueryService> { AiUsageQueryService(repository = get()) }

    // Domain Events
    single { SessionDeletedCleanupHandler(memoryService = get()) }

    single<DomainEventPublisher> {
        InProcessDomainEventPublisher(
            handlers = mapOf(
                DomainEvent.SessionDeleted::class to listOf(get<SessionDeletedCleanupHandler>()),
            ),
        )
    }

    // Application Services (use cases)
    single {
        SendMessageUseCase(
            chatService = get(),
            sessionService = get(),
            eventPublisher = get(),
        )
    }
    single {
        CreateSessionUseCase(
            sessionService = get(),
            eventPublisher = get(),
        )
    }
    single {
        DeleteSessionUseCase(
            sessionService = get(),
            eventPublisher = get(),
        )
    }
    single {
        ApplicationInitService(
            createSessionUseCase = get(),
            sessionService = get(),
        )
    }
}
