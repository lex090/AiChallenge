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
    implementation(project(":modules:presentation:compose-ui"))
    implementation(project(":modules:shared-kernel"))
    implementation(project(":modules:conversation:domain"))
    implementation(project(":modules:conversation:data"))
    implementation(project(":modules:context-management:domain"))
    implementation(project(":modules:context-management:data"))
    implementation(project(":modules:infrastructure:open-router-service"))

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    implementation(libs.decompose)
    implementation(libs.mvikotlin)
    implementation(libs.mvikotlin.main)
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "com.ai.challenge.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "AiChat"
            packageVersion = "1.0.0"
        }
    }
}
