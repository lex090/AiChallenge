package com.ai.challenge.context.repository

import com.ai.challenge.core.context.ContextManagementType
import com.ai.challenge.core.session.AgentSessionId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

class ExposedContextManagementTypeRepositoryTest {

    private lateinit var repository: ExposedContextManagementTypeRepository

    @BeforeTest
    fun setup() {
        val database = Database.connect(
            url = "jdbc:sqlite:/tmp/test_context_mgmt_repo_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
        )
        repository = ExposedContextManagementTypeRepository(database = database)
    }

    @Test
    fun `save and retrieve None type`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        repository.save(sessionId = sessionId, type = ContextManagementType.None)

        val result = repository.getBySession(sessionId = sessionId)
        assertIs<ContextManagementType.None>(value = result)
    }

    @Test
    fun `save and retrieve SummarizeOnThreshold type`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        repository.save(sessionId = sessionId, type = ContextManagementType.SummarizeOnThreshold)

        val result = repository.getBySession(sessionId = sessionId)
        assertIs<ContextManagementType.SummarizeOnThreshold>(value = result)
    }

    @Test
    fun `returns None for unknown session`() = runTest {
        val result = repository.getBySession(sessionId = AgentSessionId(value = "unknown"))
        assertIs<ContextManagementType.None>(value = result)
    }

    @Test
    fun `save overwrites existing type`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        repository.save(sessionId = sessionId, type = ContextManagementType.None)
        repository.save(sessionId = sessionId, type = ContextManagementType.SummarizeOnThreshold)

        val result = repository.getBySession(sessionId = sessionId)
        assertIs<ContextManagementType.SummarizeOnThreshold>(value = result)
    }

    @Test
    fun `delete removes entry`() = runTest {
        val sessionId = AgentSessionId(value = "s1")
        repository.save(sessionId = sessionId, type = ContextManagementType.SummarizeOnThreshold)
        repository.delete(sessionId = sessionId)

        val result = repository.getBySession(sessionId = sessionId)
        assertIs<ContextManagementType.None>(value = result)
    }

    @Test
    fun `different sessions have independent types`() = runTest {
        val s1 = AgentSessionId(value = "s1")
        val s2 = AgentSessionId(value = "s2")
        repository.save(sessionId = s1, type = ContextManagementType.None)
        repository.save(sessionId = s2, type = ContextManagementType.SummarizeOnThreshold)

        assertIs<ContextManagementType.None>(value = repository.getBySession(sessionId = s1))
        assertIs<ContextManagementType.SummarizeOnThreshold>(value = repository.getBySession(sessionId = s2))
    }
}
