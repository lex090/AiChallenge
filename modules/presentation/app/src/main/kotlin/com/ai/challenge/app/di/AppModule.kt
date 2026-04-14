package com.ai.challenge.app.di

import com.ai.challenge.conversation.impl.AiBranchService
import com.ai.challenge.conversation.impl.AiChatService
import com.ai.challenge.conversation.impl.AiSessionService
import com.ai.challenge.conversation.impl.AiUsageQueryService
import com.ai.challenge.contextmanagement.strategy.BranchingContextManager
import com.ai.challenge.contextmanagement.strategy.ContextCompressorPort
import com.ai.challenge.contextmanagement.strategy.ContextModeValidatorAdapter
import com.ai.challenge.contextmanagement.strategy.ContextPreparationAdapter
import com.ai.challenge.contextmanagement.strategy.ContextStrategy
import com.ai.challenge.contextmanagement.strategy.FactExtractorPort
import com.ai.challenge.contextmanagement.strategy.PassthroughStrategy
import com.ai.challenge.contextmanagement.strategy.SlidingWindowStrategy
import com.ai.challenge.contextmanagement.strategy.StickyFactsStrategy
import com.ai.challenge.contextmanagement.strategy.SummarizeOnThresholdStrategy
import com.ai.challenge.conversation.service.BranchService
import com.ai.challenge.conversation.service.ChatService
import com.ai.challenge.conversation.service.SessionService
import com.ai.challenge.contextmanagement.model.ContextManagementType
import com.ai.challenge.sharedkernel.port.ContextManagerPort
import com.ai.challenge.sharedkernel.port.ContextModeValidatorPort
import com.ai.challenge.contextmanagement.model.ContextStrategyConfig
import com.ai.challenge.conversation.repository.AgentSessionRepository
import com.ai.challenge.contextmanagement.repository.FactRepository
import com.ai.challenge.sharedkernel.port.LlmPort
import com.ai.challenge.sharedkernel.port.TurnQueryPort
import com.ai.challenge.contextmanagement.memory.FactMemoryProvider
import com.ai.challenge.contextmanagement.memory.MemoryService
import com.ai.challenge.contextmanagement.memory.SummaryMemoryProvider
import com.ai.challenge.contextmanagement.usecase.AddSummaryUseCase
import com.ai.challenge.contextmanagement.usecase.DeleteSummaryUseCase
import com.ai.challenge.contextmanagement.usecase.GetMemoryUseCase
import com.ai.challenge.contextmanagement.usecase.UpdateFactsUseCase
import com.ai.challenge.contextmanagement.repository.SummaryRepository
import com.ai.challenge.sharedkernel.event.DomainEvent
import com.ai.challenge.sharedkernel.event.DomainEventPublisher
import com.ai.challenge.conversation.service.UsageQueryService
import com.ai.challenge.conversation.usecase.ApplicationInitService
import com.ai.challenge.conversation.usecase.CreateSessionUseCase
import com.ai.challenge.conversation.usecase.DeleteSessionUseCase
import com.ai.challenge.conversation.usecase.SendMessageUseCase
import com.ai.challenge.app.event.InProcessDomainEventPublisher
import com.ai.challenge.infrastructure.llm.OpenRouterAdapter
import com.ai.challenge.infrastructure.llm.OpenRouterService
import com.ai.challenge.contextmanagement.data.ExposedFactRepository
import com.ai.challenge.contextmanagement.data.ExposedSummaryRepository
import com.ai.challenge.contextmanagement.data.createMemoryDatabase
import com.ai.challenge.contextmanagement.data.LlmContextCompressorAdapter
import com.ai.challenge.contextmanagement.data.LlmFactExtractorAdapter
import com.ai.challenge.contextmanagement.memory.impl.DefaultMemoryService
import com.ai.challenge.contextmanagement.memory.impl.SessionDeletedCleanupHandler
import com.ai.challenge.contextmanagement.memory.impl.DefaultFactMemoryProvider
import com.ai.challenge.contextmanagement.memory.impl.DefaultSummaryMemoryProvider
import com.ai.challenge.contextmanagement.usecase.impl.DefaultAddSummaryUseCase
import com.ai.challenge.contextmanagement.usecase.impl.DefaultDeleteSummaryUseCase
import com.ai.challenge.contextmanagement.usecase.impl.DefaultGetMemoryUseCase
import com.ai.challenge.contextmanagement.usecase.impl.DefaultUpdateFactsUseCase
import com.ai.challenge.conversation.data.ExposedAgentSessionRepository
import com.ai.challenge.conversation.data.ExposedTurnQueryAdapter
import com.ai.challenge.conversation.data.createSessionDatabase
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

    // Turn Query Port
    single<TurnQueryPort> { ExposedTurnQueryAdapter(repository = get()) }

    // Context Mode Validator Port
    single<ContextModeValidatorPort> { ContextModeValidatorAdapter() }

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
    single<ContextCompressorPort> { LlmContextCompressorAdapter(llmPort = get()) }
    single<FactExtractorPort> { LlmFactExtractorAdapter(llmPort = get()) }

    // Context strategies
    single { PassthroughStrategy(turnQueryPort = get()) }
    single { SlidingWindowStrategy(turnQueryPort = get()) }
    single {
        SummarizeOnThresholdStrategy(
            turnQueryPort = get(),
            compressor = get(),
            memoryService = get(),
        )
    }
    single {
        StickyFactsStrategy(
            turnQueryPort = get(),
            memoryService = get(),
            factExtractor = get(),
        )
    }
    single { BranchingContextManager(turnQueryPort = get()) }

    single<ContextManagerPort> {
        ContextPreparationAdapter(
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
        )
    }

    // Domain services
    single<ChatService> { AiChatService(llmPort = get(), repository = get(), contextManagerPort = get()) }
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
            projectService = get(),
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
