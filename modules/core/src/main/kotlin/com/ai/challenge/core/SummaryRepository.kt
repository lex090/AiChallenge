package com.ai.challenge.core

interface SummaryRepository {
    suspend fun save(sessionId: SessionId, summary: Summary)
    suspend fun getBySession(sessionId: SessionId): List<Summary>
}
