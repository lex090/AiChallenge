# AI Agent Chat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a modular desktop chat app where a pluggable AI agent delegates to an LLM service, with Compose Multiplatform UI, Decompose navigation, and MVIKotlin state management.

**Architecture:** Stratified Design with 4 Gradle modules layered top-to-bottom: `compose-ui → ai-agent → llm-service`, plus existing `week1 → llm-service`. Each module is one architectural layer with strictly downward dependencies.

**Tech Stack:** Kotlin 2.3.20, Compose Multiplatform 1.10.3, Ktor 3.4.2, Kotlinx Serialization 1.10.0, Decompose 3.5.0, MVIKotlin 4.3.0, Koin 4.1.0, Arrow 2.1.2, Gradle 9.4.1

---

## File Map

### New files

```
gradle/libs.versions.toml                                          — updated version catalog
gradle/wrapper/gradle-wrapper.properties                           — Gradle 9.4.1
settings.gradle.kts                                                — add new modules
build.gradle.kts                                                   — add Compose + Kotlin Compose plugins

llm-service/build.gradle.kts
llm-service/src/main/kotlin/com/ai/challenge/llm/model/ChatRequest.kt
llm-service/src/main/kotlin/com/ai/challenge/llm/model/ChatResponse.kt
llm-service/src/main/kotlin/com/ai/challenge/llm/ChatScope.kt
llm-service/src/main/kotlin/com/ai/challenge/llm/OpenRouterService.kt

ai-agent/build.gradle.kts
ai-agent/src/main/kotlin/com/ai/challenge/agent/Agent.kt
ai-agent/src/main/kotlin/com/ai/challenge/agent/AgentError.kt
ai-agent/src/main/kotlin/com/ai/challenge/agent/OpenRouterAgent.kt
ai-agent/src/test/kotlin/com/ai/challenge/agent/OpenRouterAgentTest.kt

compose-ui/build.gradle.kts
compose-ui/src/main/kotlin/com/ai/challenge/ui/main.kt
compose-ui/src/main/kotlin/com/ai/challenge/ui/di/AppModule.kt
compose-ui/src/main/kotlin/com/ai/challenge/ui/model/UiMessage.kt
compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStore.kt
compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt
compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatComponent.kt
compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatContent.kt
compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootComponent.kt
compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootContent.kt
compose-ui/src/test/kotlin/com/ai/challenge/ui/chat/store/ChatStoreTest.kt
```

### Modified files

```
week1/build.gradle.kts                                             — depend on llm-service
week1/src/main/kotlin/com/ai/challenge/Task1.kt                    — update imports
week1/src/main/kotlin/com/ai/challenge/Task2.kt                    — update imports
week1/src/main/kotlin/com/ai/challenge/Task3.kt                    — update imports
week1/src/main/kotlin/com/ai/challenge/Task4.kt                    — update imports
week1/src/main/kotlin/com/ai/challenge/Task5.kt                    — update imports
```

### Deleted files

```
week1/src/main/kotlin/com/ai/challenge/OpenRouterService.kt        — moved to llm-service
```

---

### Task 1: Update Gradle Infrastructure

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`

- [ ] **Step 1: Update gradle-wrapper.properties to Gradle 9.4.1**

Replace the contents of `gradle/wrapper/gradle-wrapper.properties`:

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.4.1-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 2: Rewrite gradle/libs.versions.toml with all dependencies**

```toml
[versions]
kotlin = "2.3.20"
compose-multiplatform = "1.10.3"
ktor = "3.4.2"
kotlinx-serialization = "1.10.0"
decompose = "3.5.0"
mvikotlin = "4.3.0"
koin = "4.1.0"
arrow = "2.1.2"
slf4j = "2.0.17"

[libraries]
# Ktor
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-logging = { module = "io.ktor:ktor-client-logging", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }

# Decompose
decompose = { module = "com.arkivanov.decompose:decompose", version.ref = "decompose" }
decompose-extensions-compose = { module = "com.arkivanov.decompose:extensions-compose", version.ref = "decompose" }

# MVIKotlin
mvikotlin = { module = "com.arkivanov.mvikotlin:mvikotlin", version.ref = "mvikotlin" }
mvikotlin-main = { module = "com.arkivanov.mvikotlin:mvikotlin-main", version.ref = "mvikotlin" }
mvikotlin-extensions-coroutines = { module = "com.arkivanov.mvikotlin:mvikotlin-extensions-coroutines", version.ref = "mvikotlin" }

# Koin
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }

# Arrow
arrow-core = { module = "io.arrow-kt:arrow-core", version.ref = "arrow" }

# Logging
slf4j-nop = { module = "org.slf4j:slf4j-nop", version.ref = "slf4j" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
```

- [ ] **Step 3: Update settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "AiChallenge"

include("llm-service")
include("ai-agent")
include("compose-ui")
include("week1")
```

- [ ] **Step 4: Update root build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}
```

- [ ] **Step 5: Verify Gradle sync**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew --version`
Expected: Gradle 9.4.1

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml gradle/wrapper/gradle-wrapper.properties settings.gradle.kts build.gradle.kts
git commit -m "Update Gradle to 9.4.1, add version catalog entries for all dependencies"
```

---

### Task 2: Create llm-service Module

**Files:**
- Create: `llm-service/build.gradle.kts`
- Create: `llm-service/src/main/kotlin/com/ai/challenge/llm/model/ChatRequest.kt`
- Create: `llm-service/src/main/kotlin/com/ai/challenge/llm/model/ChatResponse.kt`
- Create: `llm-service/src/main/kotlin/com/ai/challenge/llm/ChatScope.kt`
- Create: `llm-service/src/main/kotlin/com/ai/challenge/llm/OpenRouterService.kt`

- [ ] **Step 1: Create llm-service/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.ai.challenge"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.slf4j.nop)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
```

- [ ] **Step 2: Create ChatRequest.kt**

```kotlin
package com.ai.challenge.llm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Double? = null,
    @SerialName("presence_penalty") val presencePenalty: Double? = null,
    val stop: List<String>? = null,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
)

@Serializable
data class ResponseFormat(val type: String)

@Serializable
data class Message(val role: String, val content: String)
```

- [ ] **Step 3: Create ChatResponse.kt**

```kotlin
package com.ai.challenge.llm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
    val model: String? = null,
    val usage: Usage? = null,
    val error: ErrorBody? = null,
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: Message,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

@Serializable
data class ErrorBody(val message: String? = null, val code: Int? = null)
```

- [ ] **Step 4: Create ChatScope.kt**

```kotlin
package com.ai.challenge.llm

import com.ai.challenge.llm.model.ChatRequest
import com.ai.challenge.llm.model.Message
import com.ai.challenge.llm.model.ResponseFormat

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class OpenRouterDsl

@OpenRouterDsl
class ChatScope(private val model: String) {
    private val messages = mutableListOf<Message>()

    var temperature: Double? = null
    var maxTokens: Int? = null
    var topP: Double? = null
    var frequencyPenalty: Double? = null
    var presencePenalty: Double? = null
    var stop: List<String>? = null
    var jsonMode: Boolean = false

    fun system(content: String) {
        messages.add(Message("system", content))
    }

    fun user(content: String) {
        messages.add(Message("user", content))
    }

    fun assistant(content: String) {
        messages.add(Message("assistant", content))
    }

    fun message(role: String, content: String) {
        messages.add(Message(role, content))
    }

    fun stop(vararg values: String) {
        stop = values.toList()
    }

    fun build(): ChatRequest = ChatRequest(
        model = model,
        messages = messages.toList(),
        temperature = temperature,
        maxTokens = maxTokens,
        topP = topP,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty,
        stop = stop,
        responseFormat = if (jsonMode) ResponseFormat("json_object") else null,
    )
}
```

- [ ] **Step 5: Create OpenRouterService.kt**

```kotlin
package com.ai.challenge.llm

import com.ai.challenge.llm.model.ChatRequest
import com.ai.challenge.llm.model.ChatResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class OpenRouterService(
    private val apiKey: String,
    private val defaultModel: String? = null,
    private val client: HttpClient = createDefaultClient(),
) : AutoCloseable {

    companion object {
        private const val BASE_URL = "https://openrouter.ai/api/v1"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        fun createDefaultClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
            }
        }
    }

    suspend fun send(request: ChatRequest): ChatResponse {
        val responseText = client.post("$BASE_URL/chat/completions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(request)
        }.bodyAsText()

        return json.decodeFromString<ChatResponse>(responseText)
    }

    suspend fun chat(model: String? = null, init: @OpenRouterDsl ChatScope.() -> Unit): ChatResponse {
        val resolvedModel = model ?: defaultModel ?: error("Model must be specified either in constructor or in chat()")
        val scope = ChatScope(resolvedModel)
        scope.init()
        return send(scope.build())
    }

    suspend fun chatText(model: String? = null, init: @OpenRouterDsl ChatScope.() -> Unit): String {
        val response = chat(model, init)
        if (response.error != null) {
            error("OpenRouter API error: ${response.error.message}")
        }
        return response.choices.firstOrNull()?.message?.content
            ?: error("Empty response from OpenRouter")
    }

    override fun close() {
        client.close()
    }
}
```

- [ ] **Step 6: Verify compilation**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :llm-service:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add llm-service/
git commit -m "Create llm-service module with OpenRouterService"
```

---

### Task 3: Update week1 to Depend on llm-service

**Files:**
- Modify: `week1/build.gradle.kts`
- Delete: `week1/src/main/kotlin/com/ai/challenge/OpenRouterService.kt`
- Modify: `week1/src/main/kotlin/com/ai/challenge/Task1.kt`
- Modify: `week1/src/main/kotlin/com/ai/challenge/Task2.kt`
- Modify: `week1/src/main/kotlin/com/ai/challenge/Task3.kt`
- Modify: `week1/src/main/kotlin/com/ai/challenge/Task4.kt`
- Modify: `week1/src/main/kotlin/com/ai/challenge/Task5.kt`

- [ ] **Step 1: Add llm-service dependency to week1/build.gradle.kts**

Add to the `dependencies` block:

```kotlin
implementation(project(":llm-service"))
```

Remove from `dependencies` (now provided transitively by llm-service):

```kotlin
implementation(libs.ktor.client.core)
implementation(libs.ktor.client.cio)
implementation(libs.ktor.client.content.negotiation)
implementation(libs.ktor.serialization.kotlinx.json)
implementation(libs.ktor.client.logging)
implementation(libs.slf4j.nop)
```

The full `week1/build.gradle.kts` becomes:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.ai.challenge"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":llm-service"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
```

- [ ] **Step 2: Delete OpenRouterService.kt from week1**

```bash
rm week1/src/main/kotlin/com/ai/challenge/OpenRouterService.kt
```

- [ ] **Step 3: Add imports to Task1.kt**

Add these imports at the top of `week1/src/main/kotlin/com/ai/challenge/Task1.kt`:

```kotlin
import com.ai.challenge.llm.OpenRouterService
```

- [ ] **Step 4: Add imports to Task2.kt**

Add these imports at the top of `week1/src/main/kotlin/com/ai/challenge/Task2.kt`:

```kotlin
import com.ai.challenge.llm.OpenRouterService
```

- [ ] **Step 5: Add imports to Task3.kt**

Add these imports at the top of `week1/src/main/kotlin/com/ai/challenge/Task3.kt`:

```kotlin
import com.ai.challenge.llm.OpenRouterService
```

- [ ] **Step 6: Add imports to Task4.kt**

Add these imports at the top of `week1/src/main/kotlin/com/ai/challenge/Task4.kt`:

```kotlin
import com.ai.challenge.llm.OpenRouterService
```

- [ ] **Step 7: Add imports to Task5.kt**

Add these imports at the top of `week1/src/main/kotlin/com/ai/challenge/Task5.kt`:

```kotlin
import com.ai.challenge.llm.OpenRouterService
```

Note: Task files also use `ChatRequest`, `Message`, `ChatResponse`, `Usage`, `ErrorBody` etc. These are now in `com.ai.challenge.llm.model`. Add the following imports to each Task file that uses them:

```kotlin
import com.ai.challenge.llm.model.ChatRequest
import com.ai.challenge.llm.model.ChatResponse
import com.ai.challenge.llm.model.Message
import com.ai.challenge.llm.model.Usage
import com.ai.challenge.llm.model.ErrorBody
```

Only add the imports that each file actually uses. Read each Task file to determine which model classes it references.

- [ ] **Step 8: Verify compilation**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :week1:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add week1/ && git rm week1/src/main/kotlin/com/ai/challenge/OpenRouterService.kt
git commit -m "Migrate week1 to depend on llm-service module"
```

---

### Task 4: Create ai-agent Module — Agent Interface and AgentError

**Files:**
- Create: `ai-agent/build.gradle.kts`
- Create: `ai-agent/src/main/kotlin/com/ai/challenge/agent/Agent.kt`
- Create: `ai-agent/src/main/kotlin/com/ai/challenge/agent/AgentError.kt`

- [ ] **Step 1: Create ai-agent/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.ai.challenge"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":llm-service"))
    implementation(libs.arrow.core)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
```

- [ ] **Step 2: Create Agent.kt**

```kotlin
package com.ai.challenge.agent

import arrow.core.Either

interface Agent {
    suspend fun send(message: String): Either<AgentError, String>
}
```

- [ ] **Step 3: Create AgentError.kt**

```kotlin
package com.ai.challenge.agent

sealed interface AgentError {
    val message: String

    data class NetworkError(override val message: String) : AgentError
    data class ApiError(override val message: String) : AgentError
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :ai-agent:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add ai-agent/
git commit -m "Create ai-agent module with Agent interface and AgentError"
```

---

### Task 5: Implement OpenRouterAgent with Tests (TDD)

**Files:**
- Create: `ai-agent/src/test/kotlin/com/ai/challenge/agent/OpenRouterAgentTest.kt`
- Create: `ai-agent/src/main/kotlin/com/ai/challenge/agent/OpenRouterAgent.kt`

- [ ] **Step 1: Write failing tests**

Create `ai-agent/src/test/kotlin/com/ai/challenge/agent/OpenRouterAgentTest.kt`.

Uses Ktor `MockEngine` to create a real `OpenRouterService` with fake HTTP responses (since `OpenRouterService` is a concrete class that accepts a custom `HttpClient`):

```kotlin
package com.ai.challenge.agent

import arrow.core.Either
import com.ai.challenge.llm.OpenRouterService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OpenRouterAgentTest {

    private fun createMockClient(responseJson: String): HttpClient {
        val mockEngine = MockEngine { _ ->
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = false })
            }
        }
    }

    private fun createService(responseJson: String): OpenRouterService =
        OpenRouterService(
            apiKey = "test-key",
            client = createMockClient(responseJson),
        )

    @Test
    fun `send returns Right with response text on success`() = runTest {
        val service = createService("""
            {"choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"}}]}
        """.trimIndent())
        val agent = OpenRouterAgent(service, model = "test-model")

        val result = agent.send("Hi")

        assertIs<Either.Right<String>>(result)
        assertEquals("Hello!", result.value)
    }

    @Test
    fun `send returns Left ApiError when response has error`() = runTest {
        val service = createService("""
            {"error":{"message":"Rate limit exceeded","code":429},"choices":[]}
        """.trimIndent())
        val agent = OpenRouterAgent(service, model = "test-model")

        val result = agent.send("Hi")

        assertIs<Either.Left<AgentError>>(result)
        assertIs<AgentError.ApiError>(result.value)
        assertEquals("Rate limit exceeded", result.value.message)
    }

    @Test
    fun `send returns Left NetworkError when service throws`() = runTest {
        val mockEngine = MockEngine { _ ->
            throw RuntimeException("Connection refused")
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = false })
            }
        }
        val service = OpenRouterService(apiKey = "test-key", client = client)
        val agent = OpenRouterAgent(service, model = "test-model")

        val result = agent.send("Hi")

        assertIs<Either.Left<AgentError>>(result)
        assertIs<AgentError.NetworkError>(result.value)
        assertEquals("Connection refused", result.value.message)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :ai-agent:test`
Expected: FAIL — `OpenRouterAgent` does not exist yet

- [ ] **Step 3: Implement OpenRouterAgent**

Create `ai-agent/src/main/kotlin/com/ai/challenge/agent/OpenRouterAgent.kt`:

```kotlin
package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.catch
import com.ai.challenge.llm.OpenRouterService

class OpenRouterAgent(
    private val service: OpenRouterService,
    private val model: String,
) : Agent {

    override suspend fun send(message: String): Either<AgentError, String> = either {
        val text = catch({
            service.chatText(model = model) {
                user(message)
            }
        }) { e: Exception ->
            raise(AgentError.NetworkError(e.message ?: "Unknown error"))
        }
        text
    }
}
```

Note: The `FakeOpenRouterService` in the test has the same `chatText` signature as the real `OpenRouterService`, but is a separate class (not a subclass). Since `OpenRouterService` is a concrete class (not an interface), the test uses a fake that throws `error()` for API errors — matching the real service behavior where `chatText` throws on API errors. The `OpenRouterAgent` catches all exceptions, so API errors from the real service (thrown by `chatText`) also get caught as `NetworkError`. We map API-level errors (where `chatText` throws with "OpenRouter API error:" prefix) more precisely:

Update `OpenRouterAgent.kt` to distinguish API errors:

```kotlin
package com.ai.challenge.agent

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.catch
import com.ai.challenge.llm.OpenRouterService

class OpenRouterAgent(
    private val service: OpenRouterService,
    private val model: String,
) : Agent {

    override suspend fun send(message: String): Either<AgentError, String> = either {
        catch({
            service.chatText(model = model) {
                user(message)
            }
        }) { e: Exception ->
            val msg = e.message ?: "Unknown error"
            if (msg.startsWith("OpenRouter API error:")) {
                raise(AgentError.ApiError(msg.removePrefix("OpenRouter API error: ")))
            } else {
                raise(AgentError.NetworkError(msg))
            }
        }
    }
}
```

- [ ] **Step 4: Add test dependencies to ai-agent/build.gradle.kts**

Add to `dependencies`:

```kotlin
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
testImplementation(libs.ktor.client.mock)
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :ai-agent:test`
Expected: BUILD SUCCESSFUL, 3 tests passed

- [ ] **Step 6: Commit**

```bash
git add ai-agent/
git commit -m "Implement OpenRouterAgent with tests"
```

---

### Task 6: Create compose-ui Module — Build Config, DI, and Model

**Files:**
- Create: `compose-ui/build.gradle.kts`
- Create: `compose-ui/src/main/kotlin/com/ai/challenge/ui/di/AppModule.kt`
- Create: `compose-ui/src/main/kotlin/com/ai/challenge/ui/model/UiMessage.kt`

- [ ] **Step 1: Create compose-ui/build.gradle.kts**

```kotlin
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

group = "com.ai.challenge"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(project(":ai-agent"))
    implementation(project(":llm-service"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    implementation(libs.decompose)
    implementation(libs.decompose.extensions.compose)
    implementation(libs.mvikotlin)
    implementation(libs.mvikotlin.main)
    implementation(libs.mvikotlin.extensions.coroutines)
    implementation(libs.koin.core)
    implementation(libs.arrow.core)

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "com.ai.challenge.ui.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "AiChat"
            packageVersion = "1.0.0"
        }
    }
}
```

- [ ] **Step 2: Create UiMessage.kt**

```kotlin
package com.ai.challenge.ui.model

data class UiMessage(
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
)
```

- [ ] **Step 3: Create AppModule.kt**

```kotlin
package com.ai.challenge.ui.di

import com.ai.challenge.agent.Agent
import com.ai.challenge.agent.OpenRouterAgent
import com.ai.challenge.llm.OpenRouterService
import org.koin.dsl.module

val appModule = module {
    single {
        OpenRouterService(
            apiKey = System.getenv("OPENROUTER_API_KEY")
                ?: error("OPENROUTER_API_KEY environment variable is not set"),
        )
    }
    single<Agent> { OpenRouterAgent(get(), model = "google/gemini-2.0-flash-001") }
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :compose-ui:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add compose-ui/
git commit -m "Create compose-ui module with build config, DI, and UiMessage model"
```

---

### Task 7: Create MVIKotlin ChatStore with Tests (TDD)

**Files:**
- Create: `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStore.kt`
- Create: `compose-ui/src/test/kotlin/com/ai/challenge/ui/chat/store/ChatStoreTest.kt`
- Create: `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt`

- [ ] **Step 1: Create ChatStore.kt — Store interface**

```kotlin
package com.ai.challenge.ui.chat.store

import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store

interface ChatStore : Store<ChatStore.Intent, ChatStore.State, Nothing> {

    sealed interface Intent {
        data class SendMessage(val text: String) : Intent
    }

    data class State(
        val messages: List<UiMessage> = emptyList(),
        val isLoading: Boolean = false,
    )
}
```

- [ ] **Step 2: Write failing tests**

Create `compose-ui/src/test/kotlin/com/ai/challenge/ui/chat/store/ChatStoreTest.kt`:

```kotlin
package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.agent.Agent
import com.ai.challenge.agent.AgentError
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatStoreTest {

    @Test
    fun `initial state is empty`() {
        val store = ChatStoreFactory(DefaultStoreFactory(), FakeAgent()).create()

        assertEquals(emptyList(), store.state.messages)
        assertFalse(store.state.isLoading)

        store.dispose()
    }

    @Test
    fun `SendMessage adds user message and agent response`() = runTest {
        val agent = FakeAgent(response = Either.Right("Hello from agent!"))
        val store = ChatStoreFactory(DefaultStoreFactory(), agent).create()

        store.accept(ChatStore.Intent.SendMessage("Hi"))

        // Wait for coroutine to complete
        kotlinx.coroutines.test.advanceUntilIdle()

        val messages = store.state.messages
        assertEquals(2, messages.size)
        assertEquals(UiMessage("Hi", isUser = true), messages[0])
        assertEquals(UiMessage("Hello from agent!", isUser = false), messages[1])
        assertFalse(store.state.isLoading)

        store.dispose()
    }

    @Test
    fun `SendMessage adds error message on agent failure`() = runTest {
        val agent = FakeAgent(response = Either.Left(AgentError.NetworkError("Timeout")))
        val store = ChatStoreFactory(DefaultStoreFactory(), agent).create()

        store.accept(ChatStore.Intent.SendMessage("Hi"))

        kotlinx.coroutines.test.advanceUntilIdle()

        val messages = store.state.messages
        assertEquals(2, messages.size)
        assertEquals(UiMessage("Hi", isUser = true), messages[0])
        assertEquals(UiMessage("Timeout", isUser = false, isError = true), messages[1])
        assertFalse(store.state.isLoading)

        store.dispose()
    }
}

class FakeAgent(
    private val response: Either<AgentError, String> = Either.Right(""),
) : Agent {
    override suspend fun send(message: String): Either<AgentError, String> = response
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :compose-ui:test`
Expected: FAIL — `ChatStoreFactory` does not exist yet

- [ ] **Step 4: Implement ChatStoreFactory**

Create `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ChatStoreFactory.kt`:

```kotlin
package com.ai.challenge.ui.chat.store

import arrow.core.Either
import com.ai.challenge.agent.Agent
import com.ai.challenge.ui.model.UiMessage
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.launch

class ChatStoreFactory(
    private val storeFactory: StoreFactory,
    private val agent: Agent,
) {
    fun create(): ChatStore =
        object : ChatStore,
            Store<ChatStore.Intent, ChatStore.State, Nothing> by storeFactory.create(
                name = "ChatStore",
                initialState = ChatStore.State(),
                executorFactory = { ExecutorImpl(agent) },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Msg {
        data class UserMessage(val text: String) : Msg
        data class AgentResponse(val text: String) : Msg
        data class Error(val text: String) : Msg
        data object Loading : Msg
        data object LoadingComplete : Msg
    }

    private class ExecutorImpl(
        private val agent: Agent,
    ) : CoroutineExecutor<ChatStore.Intent, Nothing, ChatStore.State, Msg, Nothing>() {

        override fun executeIntent(intent: ChatStore.Intent) {
            when (intent) {
                is ChatStore.Intent.SendMessage -> handleSendMessage(intent.text)
            }
        }

        private fun handleSendMessage(text: String) {
            dispatch(Msg.UserMessage(text))
            dispatch(Msg.Loading)

            scope.launch {
                when (val result = agent.send(text)) {
                    is Either.Right -> dispatch(Msg.AgentResponse(result.value))
                    is Either.Left -> dispatch(Msg.Error(result.value.message))
                }
                dispatch(Msg.LoadingComplete)
            }
        }
    }

    private object ReducerImpl : com.arkivanov.mvikotlin.core.store.Reducer<ChatStore.State, Msg> {
        override fun ChatStore.State.reduce(msg: Msg): ChatStore.State =
            when (msg) {
                is Msg.UserMessage -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = true),
                )
                is Msg.AgentResponse -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = false),
                )
                is Msg.Error -> copy(
                    messages = messages + UiMessage(text = msg.text, isUser = false, isError = true),
                )
                is Msg.Loading -> copy(isLoading = true)
                is Msg.LoadingComplete -> copy(isLoading = false)
            }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :compose-ui:test`
Expected: BUILD SUCCESSFUL, 3 tests passed

Note: The `advanceUntilIdle()` in tests may not work with `DefaultStoreFactory` since its executor uses its own scope. If tests fail due to timing, replace `advanceUntilIdle()` with a small `delay(100)` or use `TestCoroutineScheduler` integration. Adjust as needed.

- [ ] **Step 6: Commit**

```bash
git add compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/store/ compose-ui/src/test/
git commit -m "Implement ChatStore with MVIKotlin, add tests"
```

---

### Task 8: Create Decompose Components

**Files:**
- Create: `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatComponent.kt`
- Create: `compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootComponent.kt`

- [ ] **Step 1: Create ChatComponent.kt**

```kotlin
package com.ai.challenge.ui.chat

import com.ai.challenge.agent.Agent
import com.ai.challenge.ui.chat.store.ChatStore
import com.ai.challenge.ui.chat.store.ChatStoreFactory
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow

class ChatComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    agent: Agent,
) : ComponentContext by componentContext {

    private val store = instanceKeeper.getStore {
        ChatStoreFactory(storeFactory, agent).create()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ChatStore.State> = store.stateFlow

    fun onSendMessage(text: String) {
        store.accept(ChatStore.Intent.SendMessage(text))
    }
}
```

- [ ] **Step 2: Create RootComponent.kt**

```kotlin
package com.ai.challenge.ui.root

import com.ai.challenge.agent.Agent
import com.ai.challenge.ui.chat.ChatComponent
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.mvikotlin.core.store.StoreFactory
import kotlinx.serialization.Serializable

class RootComponent(
    componentContext: ComponentContext,
    private val storeFactory: StoreFactory,
    private val agent: Agent,
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    val childStack: Value<ChildStack<*, Child>> =
        childStack(
            source = navigation,
            serializer = Config.serializer(),
            initialConfiguration = Config.Chat,
            handleBackButton = false,
            childFactory = ::createChild,
        )

    private fun createChild(config: Config, componentContext: ComponentContext): Child =
        when (config) {
            is Config.Chat -> Child.Chat(
                ChatComponent(
                    componentContext = componentContext,
                    storeFactory = storeFactory,
                    agent = agent,
                )
            )
        }

    sealed interface Child {
        data class Chat(val component: ChatComponent) : Child
    }

    @Serializable
    sealed interface Config {
        @Serializable
        data object Chat : Config
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :compose-ui:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatComponent.kt compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootComponent.kt
git commit -m "Create Decompose ChatComponent and RootComponent"
```

---

### Task 9: Create Compose UI

**Files:**
- Create: `compose-ui/src/main/kotlin/com/ai/challenge/ui/chat/ChatContent.kt`
- Create: `compose-ui/src/main/kotlin/com/ai/challenge/ui/root/RootContent.kt`
- Create: `compose-ui/src/main/kotlin/com/ai/challenge/ui/main.kt`

- [ ] **Step 1: Create ChatContent.kt**

```kotlin
package com.ai.challenge.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import com.ai.challenge.ui.model.UiMessage

@Composable
fun ChatContent(component: ChatComponent) {
    val state by component.state.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages) { message ->
                    MessageBubble(message)
                }
                if (state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .onKeyEvent { event ->
                            if (event.key == Key.Enter && inputText.isNotBlank() && !state.isLoading) {
                                component.onSendMessage(inputText.trim())
                                inputText = ""
                                true
                            } else {
                                false
                            }
                        },
                    placeholder = { Text("Type a message...") },
                    enabled = !state.isLoading,
                    singleLine = true,
                )
                TextButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            component.onSendMessage(inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !state.isLoading,
                ) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: UiMessage) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = when {
        message.isError -> MaterialTheme.colorScheme.errorContainer
        message.isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = when {
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        message.isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Text(
            text = message.text,
            modifier = Modifier
                .widthIn(max = 600.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .padding(12.dp),
            color = textColor,
        )
    }
}
```

- [ ] **Step 2: Create RootContent.kt**

```kotlin
package com.ai.challenge.ui.root

import androidx.compose.runtime.Composable
import com.ai.challenge.ui.chat.ChatContent
import com.arkivanov.decompose.extensions.compose.stack.Children

@Composable
fun RootContent(component: RootComponent) {
    Children(stack = component.childStack) { child ->
        when (val instance = child.instance) {
            is RootComponent.Child.Chat -> ChatContent(instance.component)
        }
    }
}
```

- [ ] **Step 3: Create main.kt**

```kotlin
package com.ai.challenge.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ai.challenge.agent.Agent
import com.ai.challenge.ui.di.appModule
import com.ai.challenge.ui.root.RootComponent
import com.ai.challenge.ui.root.RootContent
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import org.koin.core.context.startKoin

fun main() {
    val koin = startKoin {
        modules(appModule)
    }.koin

    val lifecycle = LifecycleRegistry()
    val rootComponentContext = DefaultComponentContext(lifecycle = lifecycle)

    val root = RootComponent(
        componentContext = rootComponentContext,
        storeFactory = DefaultStoreFactory(),
        agent = koin.get<Agent>(),
    )

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "AI Chat",
        ) {
            MaterialTheme {
                RootContent(root)
            }
        }
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew :compose-ui:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add compose-ui/src/main/kotlin/com/ai/challenge/ui/
git commit -m "Create Compose UI: ChatContent, RootContent, and desktop entry point"
```

---

### Task 10: End-to-End Verification

**Files:** None (verification only)

- [ ] **Step 1: Run all tests**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 2: Verify full compilation**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run the desktop app**

Run: `cd /Users/ilya/IdeaProjects/AiChallenge && OPENROUTER_API_KEY=<your-key> ./gradlew :compose-ui:run`
Expected: A desktop window opens with "AI Chat" title, showing a chat interface. Type a message, press Enter or click Send, and receive a response from the AI agent.

- [ ] **Step 4: Final commit if any adjustments were needed**

```bash
git add -A
git commit -m "Fix any issues found during end-to-end verification"
```
