import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

group = libs.versions.core.group.get()
version = libs.versions.core.version.get()

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    android {
        namespace = libs.versions.core.group.get()
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.shared)
            api(libs.compose.multiplatform.runtime)
            api(libs.compose.multiplatform.foundation)
            api(libs.compose.multiplatform.ui)
            api(libs.navigation.event.compose)
            api(libs.lifecycle.viewmodel.compose)
            api(libs.kotlinx.coroutines.core)
            api(libs.ktor.client.core)
            implementation(libs.compose.multiplatform.resources)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.sqldelight.coroutines)
        }

        androidMain.dependencies {
            api(libs.sqldelight.android.driver)
        }

        val desktopMain by getting {
            dependencies {
                api(libs.sqldelight.sqlite.driver)
            }
        }
    }
}

compose.resources {
    packageOfResClass = "dev.habita.app.core.resources"
}

sqldelight {
    databases {
        create("CoreDatabase") {
            packageName.set("dev.habita.app.core.db")
        }
    }
}