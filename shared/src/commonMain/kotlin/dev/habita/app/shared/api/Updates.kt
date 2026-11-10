package dev.habita.app.shared.api

import kotlinx.serialization.Serializable

/**
 * A published build for one platform (Android, Windows, macOS or Linux). The app compares
 * [versionCode] with its own, downloads the file and checks it against [sha256] before installing.
 */
@Serializable
data class AppRelease(
    val versionCode: Int,
    val versionName: String,
    val sha256: String,
    val sizeBytes: Long,
    val publishedAt: Long,
)