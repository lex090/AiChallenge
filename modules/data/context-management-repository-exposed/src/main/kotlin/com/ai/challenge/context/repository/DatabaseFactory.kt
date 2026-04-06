package com.ai.challenge.context.repository

import org.jetbrains.exposed.sql.Database
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

fun createContextManagementDatabase(): Database {
    val dbDir = Path(System.getProperty("user.home"), ".ai-challenge")
    dbDir.createDirectories()
    val dbPath = dbDir.resolve("context_management.db")
    return Database.connect(
        url = "jdbc:sqlite:$dbPath",
        driver = "org.sqlite.JDBC",
    )
}
