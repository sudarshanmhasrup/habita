import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
}

kotlin {
    target {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
}