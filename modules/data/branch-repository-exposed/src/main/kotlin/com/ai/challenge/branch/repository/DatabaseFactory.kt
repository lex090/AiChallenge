package com.ai.challenge.branch.repository

import org.jetbrains.exposed.sql.Database
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

fun createBranchDatabase(): Database {
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
