package com.ai.challenge.core.summary

import java.util.UUID

@JvmInline
value class SummaryId(val value: String) {
    companion object {
        fun generate(): SummaryId = SummaryId(UUID.randomUUID().toString())
    }
}
