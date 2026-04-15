package com.ai.challenge.contextmanagement.data

import com.ai.challenge.contextmanagement.model.InstructionsContent
import com.ai.challenge.contextmanagement.model.ProjectInstructions
import com.ai.challenge.sharedkernel.identity.ProjectId
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExposedProjectInstructionsRepositoryTest {

    private lateinit var database: Database
    private lateinit var repository: ExposedProjectInstructionsRepository

    @BeforeTest
    fun setUp() {
        database = Database.connect(
            url = "jdbc:sqlite:/tmp/test_cm_project_instructions_${System.nanoTime()}.db",
            driver = "org.sqlite.JDBC",
        )
        transaction(database) {
            SchemaUtils.create(ProjectInstructionsTable)
        }
        repository = ExposedProjectInstructionsRepository(database = database)
    }

    @Test
    fun `save and retrieve instructions`() = runTest {
        val projectId = ProjectId(value = "proj-1")
        val instructions = ProjectInstructions(
            projectId = projectId,
            content = InstructionsContent(value = "You are a helpful assistant"),
        )

        repository.save(instructions = instructions)
        val result = repository.getByProject(projectId = projectId)

        assertEquals(expected = instructions, actual = result)
    }

    @Test
    fun `getByProject returns null for unknown project`() = runTest {
        val result = repository.getByProject(projectId = ProjectId(value = "unknown"))

        assertNull(actual = result)
    }

    @Test
    fun `save upserts existing instructions`() = runTest {
        val projectId = ProjectId(value = "proj-1")
        val original = ProjectInstructions(
            projectId = projectId,
            content = InstructionsContent(value = "Original"),
        )
        val updated = ProjectInstructions(
            projectId = projectId,
            content = InstructionsContent(value = "Updated"),
        )

        repository.save(instructions = original)
        repository.save(instructions = updated)
        val result = repository.getByProject(projectId = projectId)

        assertEquals(expected = updated, actual = result)
    }

    @Test
    fun `deleteByProject removes instructions`() = runTest {
        val projectId = ProjectId(value = "proj-1")
        val instructions = ProjectInstructions(
            projectId = projectId,
            content = InstructionsContent(value = "To be deleted"),
        )

        repository.save(instructions = instructions)
        repository.deleteByProject(projectId = projectId)
        val result = repository.getByProject(projectId = projectId)

        assertNull(actual = result)
    }

    @Test
    fun `projects are isolated`() = runTest {
        val proj1 = ProjectInstructions(
            projectId = ProjectId(value = "proj-1"),
            content = InstructionsContent(value = "Instructions 1"),
        )
        val proj2 = ProjectInstructions(
            projectId = ProjectId(value = "proj-2"),
            content = InstructionsContent(value = "Instructions 2"),
        )

        repository.save(instructions = proj1)
        repository.save(instructions = proj2)

        assertEquals(expected = proj1, actual = repository.getByProject(projectId = proj1.projectId))
        assertEquals(expected = proj2, actual = repository.getByProject(projectId = proj2.projectId))

        repository.deleteByProject(projectId = proj1.projectId)

        assertNull(actual = repository.getByProject(projectId = proj1.projectId))
        assertEquals(expected = proj2, actual = repository.getByProject(projectId = proj2.projectId))
    }
}
