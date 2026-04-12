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

// Shared Kernel
include(":modules:shared-kernel")

// Conversation Bounded Context
include(":modules:conversation:domain")
include(":modules:conversation:data")

// Context Management Bounded Context
include(":modules:context-management:domain")
include(":modules:context-management:data")

// Infrastructure
include(":modules:infrastructure:open-router-service")

// Presentation
include(":modules:presentation:compose-ui")
include(":modules:presentation:app")

// Standalone
include(":modules:week1")
