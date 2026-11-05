rootProject.name = "Habita"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

@Suppress("UnstableApiUsage")
pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}


@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

val modules = listOf("android", "compose", "core", "shared", "server", "feature:auth", "feature:gym", "feature:expense")
modules.forEach { module ->
    include(":$module")
}

// Every build output goes under .build: the root project to .build/project, and each module to
// .build/<path>, for example .build/server and .build/feature-gym.
gradle.beforeProject {
    val directory = if (path == ":") "project" else path.removePrefix(":").replace(':', '-')
    layout.buildDirectory.set(rootProject.layout.projectDirectory.dir(".build/$directory"))
}