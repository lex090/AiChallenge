package com.ai.challenge.contextmanagement.data

import com.ai.challenge.contextmanagement.model.InstructionsContent
import com.ai.challenge.contextmanagement.model.ProjectInstructions
import com.ai.challenge.contextmanagement.repository.ProjectInstructionsRepository
import com.ai.challenge.sharedkernel.identity.ProjectId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.upsert

/**
 * Exposed implementation of [ProjectInstructionsRepository].
 *
 * Stores project instructions in [ProjectInstructionsTable] (memory.db).
 * Uses SQLite upsert for atomic insert-or-replace.
 */
class ExposedProjectInstructionsRepository(
    private val database: Database,
) : ProjectInstructionsRepository {

    override suspend fun save(instructions: ProjectInstructions) {
        newSuspendedTransaction(db = database) {
            ProjectInstructionsTable.upsert {
                it[projectId] = instructions.projectId.value
                it[content] = instructions.content.value
            }
        }
    }

    override suspend fun getByProject(projectId: ProjectId): ProjectInstructions? {
        return newSuspendedTransaction(db = database) {
            ProjectInstructionsTable
                .selectAll()
                .where { ProjectInstructionsTable.projectId eq projectId.value }
                .singleOrNull()
                ?.let { row ->
                    ProjectInstructions(
                        projectId = ProjectId(value = row[ProjectInstructionsTable.projectId]),
                        content = InstructionsContent(value = row[ProjectInstructionsTable.content]),
                    )
                }
        }
    }

    override suspend fun deleteByProject(projectId: ProjectId) {
        newSuspendedTransaction(db = database) {
            ProjectInstructionsTable.deleteWhere {
                ProjectInstructionsTable.projectId eq projectId.value
            }
        }
    }
}
