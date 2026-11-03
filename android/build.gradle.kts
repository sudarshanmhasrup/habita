import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// App settings come from local.properties, a Gradle property or an environment variable, in that
// order, so each person who clones the repository points the app at their own server.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun rawSetting(name: String): String {
    val value = localProperties.getProperty(name)
        ?: providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: ""
    return value.trim()
}

fun appSetting(name: String): String =
    "\"" + rawSetting(name).replace("\\", "\\\\").replace("\"", "\\\"") + "\""

group = libs.versions.android.group.get()
version = libs.versions.android.version.get()

android {
    namespace = libs.versions.android.group.get()
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = libs.versions.android.group.get()
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = libs.versions.android.versionCode.get().toInt()
        versionName = libs.versions.android.version.get()

        buildConfigField("String", "SERVER_URL", appSetting("HABITA_SERVER_URL"))
        buildConfigField("String", "USER_NAME", appSetting("HABITA_NAME"))
        // Off until the server is running: the app opens straight to home and keeps everything on
        // the phone. Set HABITA_LOGIN=true to ask for the login and start syncing.
        buildConfigField("boolean", "LOGIN_ENABLED", (rawSetting("HABITA_LOGIN") == "true").toString())
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.splash.screen)
    implementation(libs.activity)
    implementation(projects.compose)
}