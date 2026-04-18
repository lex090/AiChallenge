package com.ai.challenge.conversation.data

import com.ai.challenge.conversation.model.Project
import com.ai.challenge.conversation.model.ProjectName
import com.ai.challenge.conversation.repository.ProjectRepository
import com.ai.challenge.sharedkernel.identity.ProjectId
import com.ai.challenge.sharedkernel.vo.CreatedAt
import com.ai.challenge.sharedkernel.vo.SystemInstructions
import com.ai.challenge.sharedkernel.vo.UpdatedAt
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.time.Instant

/**
 * Exposed-based implementation of [ProjectRepository].
 *
 * Sole access point to the Project aggregate persistence (SQLite).
 * Uses the same database as sessions (conversation.db).
 * Creates missing tables/columns on initialization.
 */
class ExposedProjectRepository(
    private val database: Database,
) : ProjectRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(ProjectsTable)
        }
    }

    override suspend fun save(project: Project): Project {
        transaction(db = database) {
            ProjectsTable.insert {
                it[id] = project.id.value
                it[name] = project.name.value
                it[systemInstructions] = project.systemInstructions.value
                it[createdAt] = project.createdAt.value.toEpochMilliseconds()
                it[updatedAt] = project.updatedAt.value.toEpochMilliseconds()
            }
        }
        return project
    }

    override suspend fun get(id: ProjectId): Project? = transaction(database) {
        ProjectsTable.selectAll()
            .where { ProjectsTable.id eq id.value }
            .singleOrNull()
            ?.toProject()
    }

    override suspend fun delete(id: ProjectId) {
        transaction(database) {
            ProjectsTable.deleteWhere { ProjectsTable.id eq id.value }
        }
    }

    override suspend fun list(): List<Project> = transaction(database) {
        ProjectsTable.selectAll()
            .orderBy(ProjectsTable.updatedAt, SortOrder.DESC)
            .map { it.toProject() }
    }

    override suspend fun update(project: Project): Project {
        transaction(db = database) {
            ProjectsTable.update(where = { ProjectsTable.id eq project.id.value }) {
                it[name] = project.name.value
                it[systemInstructions] = project.systemInstructions.value
                it[updatedAt] = project.updatedAt.value.toEpochMilliseconds()
            }
        }
        return project
    }

    private fun ResultRow.toProject() = Project(
        id = ProjectId(value = this[ProjectsTable.id]),
        name = ProjectName(value = this[ProjectsTable.name]),
        systemInstructions = SystemInstructions(value = this[ProjectsTable.systemInstructions]),
        createdAt = CreatedAt(value = Instant.fromEpochMilliseconds(this[ProjectsTable.createdAt])),
        updatedAt = UpdatedAt(value = Instant.fromEpochMilliseconds(this[ProjectsTable.updatedAt])),
    )
}
