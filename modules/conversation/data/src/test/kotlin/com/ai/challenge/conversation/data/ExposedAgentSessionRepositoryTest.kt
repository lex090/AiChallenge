package com.ai.challenge.conversation.data

import com.ai.challenge.conversation.model.AgentSession
import com.ai.challenge.conversation.model.Branch
import com.ai.challenge.conversation.model.Cost
import com.ai.challenge.conversation.model.SessionTitle
import com.ai.challenge.conversation.model.TokenCount
import com.ai.challenge.conversation.model.Turn
import com.ai.challenge.conversation.model.TurnSequence
import com.ai.challenge.conversation.model.UsageRecord
import com.ai.challenge.sharedkernel.identity.AgentSessionId
import com.ai.challenge.sharedkernel.identity.BranchId
import com.ai.challenge.sharedkernel.identity.TurnId
import com.ai.challenge.sharedkernel.vo.ContextModeId
import com.ai.challenge.sharedkernel.vo.CreatedAt
import com.ai.challenge.sharedkernel.vo.MessageContent
import com.ai.challenge.sharedkernel.vo.UpdatedAt
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import java.math.BigDecimal
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class ExposedAgentSessionRepositoryTest {

    private lateinit var repository: ExposedAgentSessionRepository

    @BeforeTest
    fun setUp() {
        val db = Database.connect(
            url = "jdbc:sqlite:/tmp/test_session_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
            setupConnection = { conn ->
                conn.createStatement().execute("PRAGMA foreign_keys = ON")
            },
        )
        repository = ExposedAgentSessionRepository(database = db)
    }

    private fun createSession(title: String): AgentSession {
        val now = Clock.System.now()
        return AgentSession(
            id = AgentSessionId.generate(),
            title = SessionTitle(value = title),
            contextModeId = ContextModeId(value = "none"),
            projectId = null,
            userId = null,
            createdAt = CreatedAt(value = now),
            updatedAt = UpdatedAt(value = now),
        )
    }

    private fun createUsageRecord(): UsageRecord = UsageRecord(
        promptTokens = TokenCount(value = 10),
        completionTokens = TokenCount(value = 20),
        cachedTokens = TokenCount(value = 0),
        cacheWriteTokens = TokenCount(value = 0),
        reasoningTokens = TokenCount(value = 0),
        totalCost = Cost(value = BigDecimal("0.001")),
        upstreamCost = Cost(value = BigDecimal("0.0005")),
        upstreamPromptCost = Cost(value = BigDecimal("0.0003")),
        upstreamCompletionsCost = Cost(value = BigDecimal("0.0002")),
    )

    @Test
    fun `save and get round-trip`() = runTest {
        val session = createSession(title = "Test chat")
        repository.save(session = session)
        val loaded = repository.get(id = session.id)
        assertNotNull(actual = loaded)
        assertEquals(expected = "Test chat", actual = loaded.title.value)
        assertEquals(expected = session.id, actual = loaded.id)
    }

    @Test
    fun `get returns null for unknown id`() = runTest {
        assertNull(actual = repository.get(id = AgentSessionId(value = "nonexistent")))
    }

    @Test
    fun `delete removes session`() = runTest {
        val session = createSession(title = "")
        repository.save(session = session)
        repository.delete(id = session.id)
        assertNull(actual = repository.get(id = session.id))
    }

    @Test
    fun `list returns all sessions sorted by updatedAt descending`() = runTest {
        val session1 = createSession(title = "First")
        repository.save(session = session1)
        Thread.sleep(10)
        val session2 = createSession(title = "Second")
        repository.save(session = session2)

        val sessions = repository.list()
        assertEquals(expected = 2, actual = sessions.size)
        assertEquals(expected = session2.id, actual = sessions[0].id)
        assertEquals(expected = session1.id, actual = sessions[1].id)
    }

    @Test
    fun `update changes session title`() = runTest {
        val session = createSession(title = "Old")
        repository.save(session = session)
        val loaded = repository.get(id = session.id)!!
        val updated = loaded.withUpdatedTitle(newTitle = SessionTitle(value = "New"))
        repository.update(session = updated)
        assertEquals(expected = "New", actual = repository.get(id = session.id)?.title?.value)
    }

    @Test
    fun `appendTurn and getTurn round-trip`() = runTest {
        val session = createSession(title = "Chat")
        repository.save(session = session)
        val branchId = BranchId.generate()
        val branch = Branch(
            id = branchId,
            sessionId = session.id,
            sourceTurnId = null,
            turnSequence = TurnSequence(values = emptyList()),
            createdAt = session.createdAt,
        )
        repository.createBranch(branch = branch)

        val turn = Turn(
            id = TurnId.generate(),
            sessionId = session.id,
            userMessage = MessageContent(value = "Hello"),
            assistantMessage = MessageContent(value = "Hi there"),
            usage = createUsageRecord(),
            createdAt = CreatedAt(value = Clock.System.now()),
        )
        repository.appendTurn(branchId = branch.id, turn = turn)

        val loaded = repository.getTurn(turnId = turn.id)
        assertNotNull(actual = loaded)
        assertEquals(expected = "Hello", actual = loaded.userMessage.value)
        assertEquals(expected = "Hi there", actual = loaded.assistantMessage.value)
        assertEquals(expected = TokenCount(value = 10), actual = loaded.usage.promptTokens)
        assertEquals(expected = Cost(value = BigDecimal("0.001")), actual = loaded.usage.totalCost)
    }

    @Test
    fun `createBranch and getBranches round-trip`() = runTest {
        val session = createSession(title = "Chat")
        repository.save(session = session)
        val branchId = BranchId.generate()
        val branch = Branch(
            id = branchId,
            sessionId = session.id,
            sourceTurnId = null,
            turnSequence = TurnSequence(values = emptyList()),
            createdAt = session.createdAt,
        )
        repository.createBranch(branch = branch)

        val branches = repository.getBranches(sessionId = session.id)
        assertEquals(expected = 1, actual = branches.size)
        assertTrue(actual = branches[0].isMain)
    }
}
