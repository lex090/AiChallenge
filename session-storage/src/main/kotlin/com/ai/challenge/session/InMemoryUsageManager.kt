package com.ai.challenge.session

import java.util.concurrent.ConcurrentHashMap

class InMemoryUsageManager(
    private val sessionManager: AgentSessionManager,
) : UsageManager {

    private val metrics = ConcurrentHashMap<TurnId, RequestMetrics>()

    override fun record(turnId: TurnId, metrics: RequestMetrics) {
        this.metrics[turnId] = metrics
    }

    override fun getByTurn(turnId: TurnId): RequestMetrics? = metrics[turnId]

    override fun getBySession(sessionId: SessionId): Map<TurnId, RequestMetrics> {
        val turnIds = sessionManager.getHistory(sessionId)
            .map { it.id }
            .toSet()
        return metrics.filterKeys { it in turnIds }
    }

    override fun getSessionTotal(sessionId: SessionId): RequestMetrics =
        getBySession(sessionId).values.fold(RequestMetrics()) { acc, m -> acc + m }
}
