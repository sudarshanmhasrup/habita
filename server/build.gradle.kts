plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
}

group = libs.versions.server.group.get()
version = libs.versions.server.version.get()

application {
    mainClass = "dev.habita.server.ApplicationKt"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(projects.shared)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}