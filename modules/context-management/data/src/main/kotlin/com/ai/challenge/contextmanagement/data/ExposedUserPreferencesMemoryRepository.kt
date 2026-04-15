package com.ai.challenge.contextmanagement.data

import com.ai.challenge.contextmanagement.model.InstructionsContent
import com.ai.challenge.contextmanagement.model.UserPreferencesMemory
import com.ai.challenge.contextmanagement.repository.UserPreferencesMemoryRepository
import com.ai.challenge.sharedkernel.identity.UserId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.upsert

/**
 * Exposed-backed implementation of [UserPreferencesMemoryRepository].
 *
 * Persists [UserPreferencesMemory] in the "user_preferences_memory" SQLite table
 * inside memory.db. Uses atomic upsert semantics so there is always at most
 * one row per [UserId].
 */
class ExposedUserPreferencesMemoryRepository(
    private val database: Database,
) : UserPreferencesMemoryRepository {

    override suspend fun save(preferences: UserPreferencesMemory) {
        newSuspendedTransaction(db = database) {
            UserPreferencesMemoryTable.upsert {
                it[userId] = preferences.userId.value
                it[content] = preferences.content.value
            }
        }
    }

    override suspend fun getByUser(userId: UserId): UserPreferencesMemory? {
        return newSuspendedTransaction(db = database) {
            UserPreferencesMemoryTable
                .selectAll()
                .where { UserPreferencesMemoryTable.userId eq userId.value }
                .singleOrNull()
                ?.let { row ->
                    UserPreferencesMemory(
                        userId = UserId(value = row[UserPreferencesMemoryTable.userId]),
                        content = InstructionsContent(value = row[UserPreferencesMemoryTable.content]),
                    )
                }
        }
    }

    override suspend fun deleteByUser(userId: UserId) {
        newSuspendedTransaction(db = database) {
            UserPreferencesMemoryTable.deleteWhere {
                UserPreferencesMemoryTable.userId eq userId.value
            }
        }
    }
}
