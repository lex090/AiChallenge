package com.ai.challenge.core

import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

data class Fact(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val createdAt: Instant = Clock.System.now(),
)
