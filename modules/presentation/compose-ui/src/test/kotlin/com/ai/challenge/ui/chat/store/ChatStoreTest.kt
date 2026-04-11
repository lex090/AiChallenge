package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.core.branch.Branch
import com.ai.challenge.core.branch.BranchId
import com.ai.challenge.core.chat.BranchService
import com.ai.challenge.core.chat.ChatService
import com.ai.challenge.core.chat.SessionService
import com.ai.challenge.core.branch.TurnSequence
import com.ai.challenge.core.chat.model.MessageContent
import com.ai.challenge.core.chat.model.SessionTitle
import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.error.DomainError
import com.ai.challenge.core.event.DomainEvent
import com.ai.challenge.core.event.DomainEventPublisher
import com.ai.challenge.core.session.AgentSession
import com.ai.challenge.core.session.AgentSessionId
import com.ai.challenge.core.shared.CreatedAt
import com.ai.challenge.core.shared.UpdatedAt
import com.ai.challenge.core.turn.Turn
import com.ai.challenge.core.turn.TurnId
import com.ai.challenge.core.usage.UsageService
import com.ai.challenge.core.usage.model.Cost
import com.ai.challenge.core.usage.model.TokenCount
import com.ai.challenge.core.usage.model.UsageRecord
import com.ai.challenge.core.usecase.SendMessageUseCase
import com.ai.challenge.ui.model.UiMessage
import kotlin.time.Clock
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ChatStoreTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    private fun createStore(
        sendMessageUseCase: SendMessageUseCase,
        chatService: ChatService,
        sessionService: SessionService,
        usageService: UsageService,
        branchService: BranchService,
    ): ChatStore =
        ChatStoreFactory(
            storeFactory = DefaultStoreFactory(),
            sendMessageUseCase = sendMessageUseCase,
            chatService = chatService,
            sessionService = sessionService,
            usageService = usageService,
            branchService = branchService,
        ).create()

    private fun createStoreWithFake(fake: FakeServices): ChatStore {
        val sendMessageUseCase = SendMessageUseCase(
            chatService = fake,
            sessionService = fake,
            eventPublisher = NoOpDomainEventPublisher(),
        )
        return createStore(
            sendMessageUseCase = sendMessageUseCase,
            chatService = fake,
            sessionService = fake,
            usageService = fake,
            branchService = fake,
        )
    }

    @Test
    fun `initial state is empty with no session`() {
        val fake = FakeServices()
        val store = createStoreWithFake(fake = fake)
        assertEquals(emptyList(), store.state.messages)
        assertFalse(store.state.isLoading)
        assertNull(store.state.sessionId)
        assertEquals(emptyMap(), store.state.turnUsage)
        assertEquals(emptyUsage(), store.state.sessionUsage)
        store.dispose()
    }

    @Test
    fun `LoadSession sets sessionId and loads history as UiMessages`() = runTest {
        val fake = FakeServices()
        val session = (fake.create(title = SessionTitle(value = "")) as Either.Right).value
        val sessionId = session.id
        val turnId = TurnId.generate()
        fake.appendTurnDirect(
            sessionId = sessionId,
            turn = Turn(
                id = turnId,
                sessionId = sessionId,
                userMessage = MessageContent(value = "hi"),
                assistantMessage = MessageContent(value = "hello"),
                usage = emptyUsage(),
                createdAt = CreatedAt(value = Clock.System.now()),
            ),
        )
        val store = createStoreWithFake(fake = fake)

        store.accept(ChatStore.Intent.LoadSession(sessionId = sessionId))
        advanceUntilIdle()

        assertEquals(sessionId, store.state.sessionId)
        assertEquals(2, store.state.messages.size)
        assertEquals("hi", store.state.messages[0].text)
        assertEquals(true, store.state.messages[0].isUser)
        assertEquals("hello", store.state.messages[1].text)
        assertEquals(false, store.state.messages[1].isUser)
        store.dispose()
    }

    @Test
    fun `SendMessage adds user message and agent response`() = runTest {
        val turnId = TurnId.generate()
        val fake = FakeServices(sendTurnId = turnId, sendAssistantMessage = "Hello from agent!")
        val session = (fake.create(title = SessionTitle(value = "")) as Either.Right).value
        val sessionId = session.id
        val store = createStoreWithFake(fake = fake)

        store.accept(ChatStore.Intent.LoadSession(sessionId = sessionId))
        advanceUntilIdle()
        store.accept(ChatStore.Intent.SendMessage("Hi"))
        advanceUntilIdle()

        val messages = store.state.messages
        assertEquals(2, messages.size)
        assertEquals("Hi", messages[0].text)
        assertEquals(true, messages[0].isUser)
        assertEquals("Hello from agent!", messages[1].text)
        assertEquals(false, messages[1].isUser)
        assertFalse(store.state.isLoading)
        store.dispose()
    }

    @Test
    fun `SendMessage adds error message on agent failure`() = runTest {
        val fake = FakeServices(sendError = DomainError.NetworkError(message = "Timeout"))
        val session = (fake.create(title = SessionTitle(value = "")) as Either.Right).value
        val sessionId = session.id
        val store = createStoreWithFake(fake = fake)

        store.accept(ChatStore.Intent.LoadSession(sessionId = sessionId))
        advanceUntilIdle()
        store.accept(ChatStore.Intent.SendMessage("Hi"))
        advanceUntilIdle()

        val messages = store.state.messages
        assertEquals(2, messages.size)
        assertEquals(UiMessage(text = "Hi", isUser = true), messages[0])
        assertEquals(UiMessage(text = "Timeout", isUser = false, isError = true), messages[1])
        assertFalse(store.state.isLoading)
        store.dispose()
    }

    @Test
    fun `SendMessage populates turnUsage and sessionUsage`() = runTest {
        val turnId = TurnId.generate()
        val usage = UsageRecord(
            promptTokens = TokenCount(value = 100),
            completionTokens = TokenCount(value = 50),
            cachedTokens = TokenCount(value = 0),
            cacheWriteTokens = TokenCount(value = 0),
            reasoningTokens = TokenCount(value = 0),
            totalCost = Cost(value = BigDecimal("0.001")),
            upstreamCost = Cost(value = BigDecimal.ZERO),
            upstreamPromptCost = Cost(value = BigDecimal.ZERO),
            upstreamCompletionsCost = Cost(value = BigDecimal.ZERO),
        )
        val fake = FakeServices(sendTurnId = turnId, sendAssistantMessage = "Hi!", sendUsage = usage)
        val session = (fake.create(title = SessionTitle(value = "")) as Either.Right).value
        val sessionId = session.id
        val store = createStoreWithFake(fake = fake)

        store.accept(ChatStore.Intent.LoadSession(sessionId = sessionId))
        advanceUntilIdle()
        store.accept(ChatStore.Intent.SendMessage("Hello"))
        advanceUntilIdle()

        assertEquals(usage, store.state.turnUsage[turnId])
        assertEquals(usage, store.state.sessionUsage)
        store.dispose()
    }

    @Test
    fun `SendMessage accumulates session metrics across multiple turns`() = runTest {
        val turnId1 = TurnId.generate()
        val turnId2 = TurnId.generate()
        val usage1 = UsageRecord(
            promptTokens = TokenCount(value = 100),
            completionTokens = TokenCount(value = 50),
            cachedTokens = TokenCount(value = 0),
            cacheWriteTokens = TokenCount(value = 0),
            reasoningTokens = TokenCount(value = 0),
            totalCost = Cost(value = BigDecimal("0.001")),
            upstreamCost = Cost(value = BigDecimal.ZERO),
            upstreamPromptCost = Cost(value = BigDecimal.ZERO),
            upstreamCompletionsCost = Cost(value = BigDecimal.ZERO),
        )
        val usage2 = UsageRecord(
            promptTokens = TokenCount(value = 200),
            completionTokens = TokenCount(value = 100),
            cachedTokens = TokenCount(value = 0),
            cacheWriteTokens = TokenCount(value = 0),
            reasoningTokens = TokenCount(value = 0),
            totalCost = Cost(value = BigDecimal("0.002")),
            upstreamCost = Cost(value = BigDecimal.ZERO),
            upstreamPromptCost = Cost(value = BigDecimal.ZERO),
            upstreamCompletionsCost = Cost(value = BigDecimal.ZERO),
        )

        var callCount = 0
        val fake = object : FakeServices() {
            override suspend fun send(sessionId: AgentSessionId, branchId: BranchId, message: MessageContent): Either<DomainError, Turn> {
                callCount++
                val turnId = if (callCount == 1) turnId1 else turnId2
                val usage = if (callCount == 1) usage1 else usage2
                val response = if (callCount == 1) "r1" else "r2"
                val turn = Turn(
                    id = turnId,
                    sessionId = sessionId,
                    userMessage = message,
                    assistantMessage = MessageContent(value = response),
                    usage = usage,
                    createdAt = CreatedAt(value = Clock.System.now()),
                )
                appendTurnDirect(sessionId = sessionId, turn = turn)
                recordUsageDirect(turnId = turnId, sessionId = sessionId, usage = usage)
                return Either.Right(value = turn)
            }
        }
        val session = (fake.create(title = SessionTitle(value = "")) as Either.Right).value
        val sessionId = session.id
        val store = createStoreWithFake(fake = fake)

        store.accept(ChatStore.Intent.LoadSession(sessionId = sessionId))
        advanceUntilIdle()
        store.accept(ChatStore.Intent.SendMessage("Hello"))
        advanceUntilIdle()
        store.accept(ChatStore.Intent.SendMessage("Again"))
        advanceUntilIdle()

        assertEquals(usage1 + usage2, store.state.sessionUsage)
        assertEquals(2, store.state.turnUsage.size)
        store.dispose()
    }

    @Test
    fun `LoadSession loads usage data from service`() = runTest {
        val fake = FakeServices()
        val session = (fake.create(title = SessionTitle(value = "")) as Either.Right).value
        val sessionId = session.id

        val turnId1 = TurnId.generate()
        val turnId2 = TurnId.generate()
        val usage1 = UsageRecord(
            promptTokens = TokenCount(value = 10),
            completionTokens = TokenCount(value = 5),
            cachedTokens = TokenCount(value = 0),
            cacheWriteTokens = TokenCount(value = 0),
            reasoningTokens = TokenCount(value = 0),
            totalCost = Cost(value = BigDecimal("0.001")),
            upstreamCost = Cost(value = BigDecimal.ZERO),
            upstreamPromptCost = Cost(value = BigDecimal.ZERO),
            upstreamCompletionsCost = Cost(value = BigDecimal.ZERO),
        )
        val usage2 = UsageRecord(
            promptTokens = TokenCount(value = 20),
            completionTokens = TokenCount(value = 10),
            cachedTokens = TokenCount(value = 0),
            cacheWriteTokens = TokenCount(value = 0),
            reasoningTokens = TokenCount(value = 0),
            totalCost = Cost(value = BigDecimal("0.002")),
            upstreamCost = Cost(value = BigDecimal.ZERO),
            upstreamPromptCost = Cost(value = BigDecimal.ZERO),
            upstreamCompletionsCost = Cost(value = BigDecimal.ZERO),
        )

        fake.appendTurnDirect(
            sessionId = sessionId,
            turn = Turn(
                id = turnId1,
                sessionId = sessionId,
                userMessage = MessageContent(value = "a"),
                assistantMessage = MessageContent(value = "b"),
                usage = usage1,
                createdAt = CreatedAt(value = Clock.System.now()),
            ),
        )
        fake.appendTurnDirect(
            sessionId = sessionId,
            turn = Turn(
                id = turnId2,
                sessionId = sessionId,
                userMessage = MessageContent(value = "c"),
                assistantMessage = MessageContent(value = "d"),
                usage = usage2,
                createdAt = CreatedAt(value = Clock.System.now()),
            ),
        )
        fake.recordUsageDirect(turnId = turnId1, sessionId = sessionId, usage = usage1)
        fake.recordUsageDirect(turnId = turnId2, sessionId = sessionId, usage = usage2)

        val store = createStoreWithFake(fake = fake)
        store.accept(ChatStore.Intent.LoadSession(sessionId = sessionId))
        advanceUntilIdle()

        assertEquals(usage1, store.state.turnUsage[turnId1])
        assertEquals(usage2, store.state.turnUsage[turnId2])
        assertEquals(usage1 + usage2, store.state.sessionUsage)
        store.dispose()
    }

    @Test
    fun `SendMessage auto-titles session on first message`() = runTest {
        val fake = FakeServices(sendAssistantMessage = "response")
        val session = (fake.create(title = SessionTitle(value = "")) as Either.Right).value
        val sessionId = session.id
        val store = createStoreWithFake(fake = fake)

        store.accept(ChatStore.Intent.LoadSession(sessionId = sessionId))
        advanceUntilIdle()
        store.accept(ChatStore.Intent.SendMessage("Hello world, this is a long message for auto-title testing purposes"))
        advanceUntilIdle()

        val title = when (val r = fake.get(id = sessionId)) {
            is Either.Right -> r.value.title.value
            is Either.Left -> ""
        }
        assertEquals("Hello world, this is a long message for auto-title", title)
        store.dispose()
    }
}

private fun emptyUsage(): UsageRecord = UsageRecord(
    promptTokens = TokenCount(value = 0),
    completionTokens = TokenCount(value = 0),
    cachedTokens = TokenCount(value = 0),
    cacheWriteTokens = TokenCount(value = 0),
    reasoningTokens = TokenCount(value = 0),
    totalCost = Cost(value = BigDecimal.ZERO),
    upstreamCost = Cost(value = BigDecimal.ZERO),
    upstreamPromptCost = Cost(value = BigDecimal.ZERO),
    upstreamCompletionsCost = Cost(value = BigDecimal.ZERO),
)

private class NoOpDomainEventPublisher : DomainEventPublisher {
    override suspend fun publish(event: DomainEvent) {}
}

open class FakeServices(
    private val sendTurnId: TurnId = TurnId.generate(),
    private val sendAssistantMessage: String = "",
    private val sendUsage: UsageRecord = emptyUsage(),
    private val sendError: DomainError? = null,
) : ChatService, SessionService, UsageService, BranchService {

    private val sessions = ConcurrentHashMap<AgentSessionId, AgentSession>()
    private val turns = ConcurrentHashMap<TurnId, Pair<AgentSessionId, Turn>>()
    private val usageData = ConcurrentHashMap<TurnId, Pair<AgentSessionId, UsageRecord>>()
    private val mainBranches = ConcurrentHashMap<AgentSessionId, BranchId>()

    // -- ChatService --

    override suspend fun send(sessionId: AgentSessionId, branchId: BranchId, message: MessageContent): Either<DomainError, Turn> {
        if (sendError != null) return Either.Left(value = sendError)
        val turn = Turn(
            id = sendTurnId,
            sessionId = sessionId,
            userMessage = message,
            assistantMessage = MessageContent(value = sendAssistantMessage),
            usage = sendUsage,
            createdAt = CreatedAt(value = Clock.System.now()),
        )
        appendTurnDirect(sessionId = sessionId, turn = turn)
        recordUsageDirect(turnId = sendTurnId, sessionId = sessionId, usage = sendUsage)
        return Either.Right(value = turn)
    }

    // -- SessionService --

    override suspend fun create(title: SessionTitle): Either<DomainError, AgentSession> {
        val id = AgentSessionId.generate()
        val mainBranchId = BranchId.generate()
        val now = Clock.System.now()
        val session = AgentSession(
            id = id,
            title = title,
            contextManagementType = ContextManagementType.None,
            createdAt = CreatedAt(value = now),
            updatedAt = UpdatedAt(value = now),
        )
        sessions[id] = session
        mainBranches[id] = mainBranchId
        return Either.Right(value = session)
    }

    override suspend fun get(id: AgentSessionId): Either<DomainError, AgentSession> {
        val session = sessions[id]
            ?: return Either.Left(value = DomainError.SessionNotFound(id = id))
        return Either.Right(value = session)
    }

    override suspend fun delete(id: AgentSessionId): Either<DomainError, Unit> {
        sessions.remove(id)
        return Either.Right(value = Unit)
    }

    override suspend fun list(): Either<DomainError, List<AgentSession>> =
        Either.Right(value = sessions.values.toList())

    override suspend fun updateTitle(id: AgentSessionId, title: SessionTitle): Either<DomainError, AgentSession> {
        val session = sessions[id]
            ?: return Either.Left(value = DomainError.SessionNotFound(id = id))
        val updated = session.withUpdatedTitle(newTitle = title)
        sessions[id] = updated
        return Either.Right(value = updated)
    }

    override suspend fun updateContextManagementType(id: AgentSessionId, type: ContextManagementType): Either<DomainError, AgentSession> {
        val session = sessions[id]
            ?: return Either.Left(value = DomainError.SessionNotFound(id = id))
        val updated = session.withContextManagementType(type = type)
        sessions[id] = updated
        return Either.Right(value = updated)
    }

    // -- UsageService --

    override suspend fun getByTurn(turnId: TurnId): Either<DomainError, UsageRecord> {
        val record = usageData[turnId]?.second
            ?: return Either.Left(value = DomainError.TurnNotFound(id = turnId))
        return Either.Right(value = record)
    }

    override suspend fun getBySession(sessionId: AgentSessionId): Either<DomainError, Map<TurnId, UsageRecord>> =
        Either.Right(value = usageData.filter { it.value.first == sessionId }.mapValues { it.value.second })

    override suspend fun getSessionTotal(sessionId: AgentSessionId): Either<DomainError, UsageRecord> {
        val records = usageData.filter { it.value.first == sessionId }.mapValues { it.value.second }
        return Either.Right(value = records.values.fold(emptyUsage()) { acc, u -> acc + u })
    }

    // -- BranchService --

    override suspend fun create(sessionId: AgentSessionId, sourceTurnId: TurnId, fromBranchId: BranchId): Either<DomainError, Branch> =
        Either.Left(value = DomainError.ApiError(message = "Not implemented"))

    override suspend fun delete(branchId: BranchId): Either<DomainError, Unit> =
        Either.Left(value = DomainError.ApiError(message = "Not implemented"))

    override suspend fun getAll(sessionId: AgentSessionId): Either<DomainError, List<Branch>> {
        val branchId = mainBranches[sessionId] ?: return Either.Right(value = emptyList())
        val branch = Branch(
            id = branchId,
            sessionId = sessionId,
            sourceTurnId = null,
            turnSequence = TurnSequence(values = turns.values.filter { it.first == sessionId }.map { it.second.id }),
            createdAt = CreatedAt(value = Clock.System.now()),
        )
        return Either.Right(value = listOf(branch))
    }

    override suspend fun getTurns(branchId: BranchId): Either<DomainError, List<Turn>> {
        val sessionId = mainBranches.entries.firstOrNull { it.value == branchId }?.key
            ?: return Either.Right(value = emptyList())
        val all = turns.values.filter { it.first == sessionId }.map { it.second }.sortedBy { it.createdAt.value }
        return Either.Right(value = all)
    }

    // -- Direct helpers for tests --

    fun appendTurnDirect(sessionId: AgentSessionId, turn: Turn): TurnId {
        turns[turn.id] = sessionId to turn
        return turn.id
    }

    fun recordUsageDirect(turnId: TurnId, sessionId: AgentSessionId, usage: UsageRecord) {
        usageData[turnId] = sessionId to usage
    }
}
