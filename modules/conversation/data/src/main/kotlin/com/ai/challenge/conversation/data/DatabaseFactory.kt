package com.ai.challenge.conversation.data

import org.jetbrains.exposed.sql.Database
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

/**
 * Factory function for creating the session SQLite database connection.
 *
 * Creates the database file at `~/.ai-challenge/sessions.db`.
 * Enables SQLite foreign key enforcement via PRAGMA.
 */
fun createSessionDatabase(): Database {
    val dbDir = Path(System.getProperty("user.home"), ".ai-challenge")
    dbDir.createDirectories()
    val dbPath = dbDir.resolve("sessions.db")
    return Database.connect(
        url = "jdbc:sqlite:$dbPath",
        driver = "org.sqlite.JDBC",
        setupConnection = { conn ->
            conn.createStatement().execute("PRAGMA foreign_keys = ON")
        },
    )
}
