package com.ai.challenge.conversation.data

import com.ai.challenge.conversation.model.User
import com.ai.challenge.conversation.model.UserName
import com.ai.challenge.conversation.model.UserPreferences
import com.ai.challenge.conversation.repository.UserRepository
import com.ai.challenge.sharedkernel.identity.UserId
import com.ai.challenge.sharedkernel.vo.CreatedAt
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
 * Exposed-based implementation of [UserRepository].
 *
 * Sole access point to the User aggregate persistence (SQLite).
 * Uses the same database as sessions (conversation.db).
 * Creates missing tables/columns on initialization.
 */
class ExposedUserRepository(
    private val database: Database,
) : UserRepository {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(UsersTable)
        }
    }

    override suspend fun save(user: User): User {
        transaction(db = database) {
            UsersTable.insert {
                it[id] = user.id.value
                it[name] = user.name.value
                it[preferences] = user.preferences.value
                it[createdAt] = user.createdAt.value.toEpochMilliseconds()
                it[updatedAt] = user.updatedAt.value.toEpochMilliseconds()
            }
        }
        return user
    }

    override suspend fun get(id: UserId): User? = transaction(database) {
        UsersTable.selectAll()
            .where { UsersTable.id eq id.value }
            .singleOrNull()
            ?.toUser()
    }

    override suspend fun delete(id: UserId) {
        transaction(database) {
            UsersTable.deleteWhere { UsersTable.id eq id.value }
        }
    }

    override suspend fun list(): List<User> = transaction(database) {
        UsersTable.selectAll()
            .orderBy(UsersTable.updatedAt, SortOrder.DESC)
            .map { it.toUser() }
    }

    override suspend fun update(user: User): User {
        transaction(db = database) {
            UsersTable.update(where = { UsersTable.id eq user.id.value }) {
                it[name] = user.name.value
                it[preferences] = user.preferences.value
                it[updatedAt] = user.updatedAt.value.toEpochMilliseconds()
            }
        }
        return user
    }

    private fun ResultRow.toUser() = User(
        id = UserId(value = this[UsersTable.id]),
        name = UserName(value = this[UsersTable.name]),
        preferences = UserPreferences(value = this[UsersTable.preferences]),
        createdAt = CreatedAt(value = Instant.fromEpochMilliseconds(this[UsersTable.createdAt])),
        updatedAt = UpdatedAt(value = Instant.fromEpochMilliseconds(this[UsersTable.updatedAt])),
    )
}
