package com.ai.challenge.session

interface UsageManager {
    fun record(turnId: TurnId, metrics: RequestMetrics)
    fun getByTurn(turnId: TurnId): RequestMetrics?
    fun getBySession(sessionId: SessionId): Map<TurnId, RequestMetrics>
    fun getSessionTotal(sessionId: SessionId): RequestMetrics
}
