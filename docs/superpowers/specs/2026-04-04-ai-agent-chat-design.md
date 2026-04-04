# AI Agent Chat — Design Spec

## Overview

Desktop-приложение с чат-интерфейсом для взаимодействия с AI-агентом. Агент — абстрактная сущность, инкапсулирующая работу с LLM-сервисом. UI — подключаемый модуль, который можно заменить на CLI или другой фронтенд.

## Stack

| Library               | Version |
|-----------------------|---------|
| Kotlin                | 2.3.20  |
| Compose Multiplatform | 1.10.3  |
| Ktor                  | 3.4.2   |
| Kotlinx Serialization | 1.10.0  |
| Decompose             | 3.5.0   |
| MVIKotlin             | 4.3.0   |
| Koin                  | 4.1.0   |
| Arrow                 | 2.1.2   |
| Gradle                | 9.4.1   |

Target: Desktop JVM (JDK 21).

## Architecture — Stratified Design

Каждый модуль = один слой. Зависимости строго сверху вниз.

```
compose-ui  →  ai-agent  →  llm-service
week1  →  llm-service
```

Ни один модуль не знает о модулях выше себя. `llm-service` и `ai-agent` — чистый Kotlin/JVM без UI-зависимостей.

---

## Module: llm-service (Data Layer)

**Package:** `com.ai.challenge.llm`

Переезд `OpenRouterService` из `week1` с разнесением по файлам. Логика и API без изменений.

### Structure

```
llm-service/src/main/kotlin/com/ai/challenge/llm/
├── OpenRouterService.kt       — HTTP-клиент, send/chat/chatText
├── model/
│   ├── ChatRequest.kt         — ChatRequest, Message, ResponseFormat
│   └── ChatResponse.kt        — ChatResponse, Choice, Usage, ErrorBody
└── ChatScope.kt               — DSL-билдер (@OpenRouterDsl)
```

### Dependencies

- ktor-client-core, ktor-client-cio, ktor-client-content-negotiation, ktor-client-logging
- ktor-serialization-kotlinx-json
- slf4j-nop

---

## Module: ai-agent (Domain Layer)

**Package:** `com.ai.challenge.agent`

Абстракция агента. На текущем этапе — обёртка над LLM-сервисом. Позже расширяется историей чатов, инструментами и т.д.

### Structure

```
ai-agent/src/main/kotlin/com/ai/challenge/agent/
├── Agent.kt                   — интерфейс Agent
├── AgentError.kt              — sealed interface AgentError
└── OpenRouterAgent.kt         — реализация
```

### Agent Interface

```kotlin
interface Agent {
    suspend fun send(message: String): Either<AgentError, String>
}

sealed interface AgentError {
    data class NetworkError(val message: String) : AgentError
    data class ApiError(val message: String) : AgentError
}
```

### OpenRouterAgent

```kotlin
class OpenRouterAgent(
    private val service: OpenRouterService,
    private val model: String
) : Agent {
    override suspend fun send(message: String): Either<AgentError, String> = either {
        catch({ service.chatText(model = model) { user(message) } }) {
            raise(AgentError.NetworkError(it.message ?: "Unknown error"))
        }
    }
}
```

### Dependencies

- project(":llm-service")
- arrow-core

---

## Module: compose-ui (Presentation + UI Layer)

**Package:** `com.ai.challenge.ui`

Decompose для компонентной архитектуры и навигации, MVIKotlin для управления состоянием, Compose для рендеринга, Koin для DI.

### Structure

```
compose-ui/src/main/kotlin/com/ai/challenge/ui/
├── main.kt                              — desktop application entry point
├── di/
│   └── AppModule.kt                     — Koin module
├── root/
│   ├── RootComponent.kt                 — Decompose root, навигация через Child Stack
│   └── RootContent.kt                   — Compose-обёртка, маршрутизация
├── chat/
│   ├── ChatComponent.kt                 — Decompose компонент, владеет MVIKotlin Store
│   ├── store/
│   │   ├── ChatStore.kt                 — Store интерфейс (Intent, State, Label)
│   │   └── ChatStoreFactory.kt          — Executor + Reducer, вызов Agent.send()
│   └── ChatContent.kt                   — Compose UI: список сообщений + поле ввода
└── model/
    └── UiMessage.kt                     — data class
```

### DI — Koin

```kotlin
val appModule = module {
    single { OpenRouterService() }
    single<Agent> { OpenRouterAgent(get(), model = "google/gemini-2.0-flash-001") }
}
```

### MVIKotlin Store Contract

```kotlin
sealed interface Intent {
    data class SendMessage(val text: String) : Intent
}

data class State(
    val messages: List<UiMessage> = emptyList(),
    val isLoading: Boolean = false
)
```

### UI Model

```kotlin
data class UiMessage(
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false
)
```

### Data Flow

```
User input
  → ChatContent dispatches Intent.SendMessage
    → ChatStore Executor: adds user message, isLoading = true
      → Agent.send(text)
        → Either.Right: adds agent message, isLoading = false
        → Either.Left: adds error message (isError = true), isLoading = false
          → ChatContent re-renders with new State
```

### Entry Point

```kotlin
fun main() = application {
    startKoin { modules(appModule) }
    val root = RootComponent(DefaultComponentContext(...), getKoin())
    Window(title = "AI Chat") {
        RootContent(root)
    }
}
```

### Dependencies

- project(":ai-agent")
- compose-desktop (currentOs)
- decompose, decompose-extensions-compose
- mvikotlin, mvikotlin-main, mvikotlin-extensions-coroutines
- koin-core
- arrow-core

---

## Module: week1 (Existing)

Без изменений логики. `OpenRouterService.kt` удаляется, добавляется зависимость `implementation(project(":llm-service"))`. Task1-Task5 обновляют импорты на `com.ai.challenge.llm`.

---

## Error Handling

Arrow `Either` на границе `Agent` → presentation. Агент возвращает `Either<AgentError, String>`. Store матчит результат:

```kotlin
when (val result = agent.send(text)) {
    is Either.Right -> dispatch(Msg.AgentResponse(result.value))
    is Either.Left -> dispatch(Msg.Error(result.value.message))
}
```

Ошибки отображаются как сообщения в чате с `isError = true`. Без retry, snackbar, диалогов.

---

## Testing

### ai-agent
- Unit-тесты `OpenRouterAgent` с fake `OpenRouterService`
- Проверка Either.Right при успехе, Either.Left при исключении
- Без мок-фреймворков — ручные fake-реализации

### compose-ui
- Unit-тесты `ChatStoreFactory` с fake `Agent`
- Проверка обработки Intent.SendMessage → корректное обновление State
- Fake Agent через Koin test module

### llm-service
- Не тестируется на этом этапе (HTTP-клиент, обёртка над API)

### week1
- Не трогаем

---

## Out of Scope (Future)

- История чатов (персистенция)
- Tool use / function calling
- Несколько экранов / навигация между чатами
- CLI-модуль
- Стриминг ответов
- Выбор модели в UI
