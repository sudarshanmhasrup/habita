import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

group = libs.versions.compose.group.get()
version = libs.versions.compose.version.get()

kotlin {
    jvm("desktop") {
        compilerOptions {
            JvmTarget.JVM_11
        }
    }
}