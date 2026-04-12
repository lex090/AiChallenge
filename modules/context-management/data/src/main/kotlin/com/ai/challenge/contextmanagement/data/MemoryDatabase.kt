package com.ai.challenge.contextmanagement.data

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

/**
 * Factory function for creating the memory SQLite database.
 *
 * Creates the database file at `~/.ai-challenge/memory.db`,
 * ensures the directory exists, enables foreign keys,
 * and creates missing tables on first run.
 */
fun createMemoryDatabase(): Database {
    val dbDir = Path(System.getProperty("user.home"), ".ai-challenge")
    dbDir.createDirectories()
    val dbPath = dbDir.resolve("memory.db")
    val db = Database.connect(
        url = "jdbc:sqlite:$dbPath",
        driver = "org.sqlite.JDBC",
        setupConnection = { conn ->
            conn.createStatement().execute("PRAGMA foreign_keys = ON")
        },
    )
    transaction(db) {
        SchemaUtils.createMissingTablesAndColumns(FactsTable, SummariesTable)
    }
    return db
}
