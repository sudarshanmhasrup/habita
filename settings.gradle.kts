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

gradle.beforeProject {
    if (name == rootProject.name) {
        layout.buildDirectory.set(rootProject.layout.projectDirectory.dir(".build/project"))
    }
}

val modules = setOf("android", "compose", "shared")
modules.forEach { module ->
    include(":$module")
}

gradle.beforeProject {
    if (name in modules) {
        layout.buildDirectory.set(rootProject.layout.projectDirectory.dir(".build/$name"))
    }
}