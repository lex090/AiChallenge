package com.ai.challenge.cost.repository

import org.jetbrains.exposed.sql.Database
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

fun createCostDatabase(): Database {
    val dbDir = Path(System.getProperty("user.home"), ".ai-challenge")
    dbDir.createDirectories()
    val dbPath = dbDir.resolve("sessions.db")
    return Database.connect(
        url = "jdbc:sqlite:$dbPath",
        driver = "org.sqlite.JDBC",
    )
}
