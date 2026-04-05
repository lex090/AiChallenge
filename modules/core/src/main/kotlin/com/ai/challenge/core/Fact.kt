package com.ai.challenge.core

import kotlin.time.Clock
import kotlin.time.Instant

data class Fact(
    val key: String,
    val value: String,
    val updatedAt: Instant = Clock.System.now(),
)
