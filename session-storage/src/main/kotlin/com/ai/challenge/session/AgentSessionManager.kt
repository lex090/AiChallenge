package com.ai.challenge.session

interface AgentSessionManager {
    fun createSession(title: String = ""): SessionId
    fun getSession(id: SessionId): AgentSession?
    fun deleteSession(id: SessionId): Boolean
    fun listSessions(): List<AgentSession>
    fun getHistory(id: SessionId, limit: Int? = null): List<Turn>
    fun appendTurn(id: SessionId, turn: Turn)
    fun updateTitle(id: SessionId, title: String)
}
