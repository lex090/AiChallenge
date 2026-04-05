import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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
    implementation(project(":modules:domain:ai-agent"))
    implementation(project(":modules:data:llm-service"))
    implementation(project(":modules:core"))
    implementation(project(":modules:data:session-repository-exposed"))
    implementation(project(":modules:data:turn-repository-exposed"))
    implementation(project(":modules:data:token-repository-exposed"))
    implementation(project(":modules:data:cost-repository-exposed"))

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation(libs.decompose)
    implementation(libs.decompose.extensions.compose)
    implementation(libs.mvikotlin)
    implementation(libs.mvikotlin.main)
    implementation(libs.mvikotlin.extensions.coroutines)
    implementation(libs.koin.core)
    implementation(libs.arrow.core)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.datetime)
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
