package com.ai.challenge.ui.di

import com.ai.challenge.agent.Agent
import com.ai.challenge.agent.OpenRouterAgent
import com.ai.challenge.llm.OpenRouterService
import com.ai.challenge.session.AgentSessionManager
import com.ai.challenge.session.InMemorySessionManager
import org.koin.dsl.module

val appModule = module {
    single {
        OpenRouterService(
            apiKey = System.getenv("OPENROUTER_API_KEY")
                ?: error("OPENROUTER_API_KEY environment variable is not set"),
        )
    }
    single<AgentSessionManager> { InMemorySessionManager() }
    single<Agent> { OpenRouterAgent(get(), model = "google/gemini-2.0-flash-001", sessionManager = get()) }
}
