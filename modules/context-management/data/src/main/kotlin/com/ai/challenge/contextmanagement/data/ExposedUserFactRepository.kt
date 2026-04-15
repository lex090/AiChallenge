package com.ai.challenge.contextmanagement.data

import com.ai.challenge.contextmanagement.model.FactKey
import com.ai.challenge.contextmanagement.model.FactValue
import com.ai.challenge.contextmanagement.model.UserFact
import com.ai.challenge.contextmanagement.repository.UserFactRepository
import com.ai.challenge.sharedkernel.identity.UserId
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

/**
 * Exposed-backed implementation of [UserFactRepository].
 *
 * Persists [UserFact] entities in the "user_facts" SQLite table inside memory.db.
 * Uses replace-all semantics: [saveAll] deletes all existing facts for the user
 * and inserts the new set atomically within a single transaction.
 */
class ExposedUserFactRepository(
    private val database: Database,
) : UserFactRepository {

    override suspend fun saveAll(userId: UserId, facts: List<UserFact>) {
        newSuspendedTransaction(db = database) {
            UserFactsTable.deleteWhere { UserFactsTable.userId eq userId.value }
            UserFactsTable.batchInsert(data = facts) { fact ->
                this[UserFactsTable.userId] = fact.userId.value
                this[UserFactsTable.category] = fact.category.toStorageString()
                this[UserFactsTable.key] = fact.key.value
                this[UserFactsTable.value] = fact.value.value
            }
        }
    }

    override suspend fun getByUser(userId: UserId): List<UserFact> {
        return newSuspendedTransaction(db = database) {
            UserFactsTable
                .selectAll()
                .where { UserFactsTable.userId eq userId.value }
                .map { row ->
                    UserFact(
                        userId = UserId(value = row[UserFactsTable.userId]),
                        category = row[UserFactsTable.category].toFactCategory(),
                        key = FactKey(value = row[UserFactsTable.key]),
                        value = FactValue(value = row[UserFactsTable.value]),
                    )
                }
        }
    }

    override suspend fun deleteByUser(userId: UserId) {
        newSuspendedTransaction(db = database) {
            UserFactsTable.deleteWhere { UserFactsTable.userId eq userId.value }
        }
    }
}
