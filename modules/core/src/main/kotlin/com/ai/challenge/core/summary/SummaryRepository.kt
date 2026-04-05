package com.ai.challenge.core.summary

import com.ai.challenge.core.session.SessionId

interface SummaryRepository {
    suspend fun save(sessionId: SessionId, summary: Summary)
    suspend fun getBySession(sessionId: SessionId): List<Summary>
}
