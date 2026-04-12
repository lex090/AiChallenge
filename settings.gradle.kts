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

// Layer 0: Foundation
include(":modules:core")
include(":modules:shared-kernel")

// Conversation Bounded Context
include(":modules:conversation:domain")
include(":modules:conversation:data")

// Context Management Bounded Context
include(":modules:context-management:domain")
include(":modules:context-management:data")

// Infrastructure
include(":modules:infrastructure:open-router-service")

// Layer 1: Data
include(":modules:data:open-router-service")
include(":modules:data:session-repository-exposed")
include(":modules:data:memory-repository-exposed")

// Layer 2: Domain
include(":modules:domain:ai-agent")
include(":modules:domain:context-manager")
include(":modules:domain:memory-service")

// Layer 3: Presentation
include(":modules:presentation:compose-ui")
include(":modules:presentation:app")

// Standalone
include(":modules:week1")
