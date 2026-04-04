package com.ai.challenge.session

import java.util.concurrent.ConcurrentHashMap

class InMemorySessionManager : AgentSessionManager {

    private val sessions = ConcurrentHashMap<SessionId, AgentSession>()

    override fun createSession(title: String): SessionId {
        val session = AgentSession(id = SessionId.generate(), title = title)
        sessions[session.id] = session
        return session.id
    }

    override fun getSession(id: SessionId): AgentSession? = sessions[id]

    override fun deleteSession(id: SessionId): Boolean =
        sessions.remove(id) != null

    override fun listSessions(): List<AgentSession> =
        sessions.values.sortedByDescending { it.updatedAt }

    override fun getHistory(id: SessionId, limit: Int?): List<Turn> {
        val history = sessions[id]?.history ?: return emptyList()
        return if (limit != null) history.takeLast(limit) else history
    }

    override fun appendTurn(id: SessionId, turn: Turn) {
        sessions.computeIfPresent(id) { _, session -> session.addTurn(turn) }
    }

    override fun updateTitle(id: SessionId, title: String) {
        sessions.computeIfPresent(id) { _, session ->
            session.copy(title = title, updatedAt = kotlinx.datetime.Clock.System.now())
        }
    }
}
