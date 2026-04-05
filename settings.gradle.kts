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

// Layer 1: Data
include(":modules:data:open-router-service")
include(":modules:data:session-repository-exposed")
include(":modules:data:turn-repository-exposed")
include(":modules:data:token-repository-exposed")
include(":modules:data:cost-repository-exposed")
include(":modules:data:summary-repository-exposed")
include(":modules:data:context-compressor-llm")
include(":modules:data:fact-extractor-llm")
include(":modules:data:fact-repository-exposed")
include(":modules:data:branch-repository-exposed")

// Layer 2: Domain
include(":modules:domain:ai-agent")
include(":modules:domain:context-manager")

// Layer 3: Presentation
include(":modules:presentation:compose-ui")
include(":modules:presentation:app")

// Standalone
include(":modules:week1")
