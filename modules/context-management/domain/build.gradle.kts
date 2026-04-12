plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.ai.challenge.context-management"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":modules:shared-kernel"))
    implementation(libs.arrow.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
