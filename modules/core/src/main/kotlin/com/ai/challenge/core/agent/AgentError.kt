package com.ai.challenge.core.agent

sealed interface AgentError {
    val message: String

    data class NetworkError(override val message: String) : AgentError
    data class ApiError(override val message: String) : AgentError
    data class NotFound(override val message: String) : AgentError
    data class DatabaseError(override val message: String) : AgentError
}
