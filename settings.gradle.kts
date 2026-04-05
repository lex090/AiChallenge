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
include("core")
include("ai-agent")
include("session-storage")
include("session-repository-exposed")
include("compose-ui")
include("week1")
