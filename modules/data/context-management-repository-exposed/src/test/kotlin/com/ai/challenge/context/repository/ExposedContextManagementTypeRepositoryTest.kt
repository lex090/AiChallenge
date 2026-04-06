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
        repository = ExposedContextManagementTypeRepository(database)
    }

    @Test
    fun `save and retrieve None type`() = runTest {
        val sessionId = AgentSessionId("s1")
        repository.save(sessionId, ContextManagementType.None)

        val result = repository.getBySession(sessionId)
        assertIs<ContextManagementType.None>(result)
    }

    @Test
    fun `save and retrieve SummarizeOnThreshold type`() = runTest {
        val sessionId = AgentSessionId("s1")
        repository.save(sessionId, ContextManagementType.SummarizeOnThreshold)

        val result = repository.getBySession(sessionId)
        assertIs<ContextManagementType.SummarizeOnThreshold>(result)
    }

    @Test
    fun `returns None for unknown session`() = runTest {
        val result = repository.getBySession(AgentSessionId("unknown"))
        assertIs<ContextManagementType.None>(result)
    }

    @Test
    fun `save overwrites existing type`() = runTest {
        val sessionId = AgentSessionId("s1")
        repository.save(sessionId, ContextManagementType.None)
        repository.save(sessionId, ContextManagementType.SummarizeOnThreshold)

        val result = repository.getBySession(sessionId)
        assertIs<ContextManagementType.SummarizeOnThreshold>(result)
    }

    @Test
    fun `delete removes entry`() = runTest {
        val sessionId = AgentSessionId("s1")
        repository.save(sessionId, ContextManagementType.SummarizeOnThreshold)
        repository.delete(sessionId)

        val result = repository.getBySession(sessionId)
        assertIs<ContextManagementType.None>(result)
    }

    @Test
    fun `different sessions have independent types`() = runTest {
        val s1 = AgentSessionId("s1")
        val s2 = AgentSessionId("s2")
        repository.save(s1, ContextManagementType.None)
        repository.save(s2, ContextManagementType.SummarizeOnThreshold)

        assertIs<ContextManagementType.None>(repository.getBySession(s1))
        assertIs<ContextManagementType.SummarizeOnThreshold>(repository.getBySession(s2))
    }
}
