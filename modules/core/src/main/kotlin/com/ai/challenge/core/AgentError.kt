package com.ai.challenge.core

sealed interface AgentError {
    val message: String

    data class NetworkError(override val message: String) : AgentError
    data class ApiError(override val message: String) : AgentError
}
