plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

group = libs.versions.server.group.get()
version = libs.versions.server.version.get()

application {
    mainClass = "dev.habita.server.ApplicationKt"
}

kotlin {
    jvmToolchain(21)
}

sqldelight {
    databases {
        create("HabitaDatabase") {
            packageName.set("dev.habita.server.db")
        }
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.sqldelight.sqlite.driver)
    implementation(libs.bcrypt)
    implementation(libs.logback)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.kotlin.test)
}

// Paths like program/PROGRAM.md resolve from the repository root, locally and in tests.
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}

tasks.test {
    useJUnitPlatform()
    workingDir = rootDir
}