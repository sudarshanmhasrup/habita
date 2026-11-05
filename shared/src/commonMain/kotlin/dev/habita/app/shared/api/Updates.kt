package dev.habita.app.shared.api

import kotlinx.serialization.Serializable

/**
 * A published Android build. The app compares [versionCode] with its own, downloads the APK and
 * checks it against [sha256] before handing it to the installer.
 */
@Serializable
data class AndroidRelease(
    val versionCode: Int,
    val versionName: String,
    val sha256: String,
    val sizeBytes: Long,
    val publishedAt: Long,
)