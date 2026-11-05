package dev.habita.server.updates

import dev.habita.app.shared.api.AndroidRelease
import dev.habita.server.HabitaJson
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Android builds published by CI, kept as files in [directory]: the APKs and `latest.json`
 * describing the newest. Only the newest [KEEP] APKs stay.
 */
class Releases(private val directory: File) {
    private val latestFile = File(directory, "latest.json")

    @Synchronized
    fun latest(): AndroidRelease? =
        latestFile.takeIf { it.isFile }?.let { runCatching { HabitaJson.decodeFromString<AndroidRelease>(it.readText()) }.getOrNull() }

    fun apk(release: AndroidRelease): File = File(directory, "habita-${release.versionCode}.apk")

    /**
     * Saves [apk] as the newest build. Refuses anything that isn't an APK, or isn't newer than
     * the current one, so an old build can never be offered as an update.
     */
    @Synchronized
    fun publish(apk: ByteArray, versionCode: Int, versionName: String, now: Long): AndroidRelease {
        require(apk.size > 4 && apk[0] == 'P'.code.toByte() && apk[1] == 'K'.code.toByte()) { "Not an APK" }
        require(versionName.isNotBlank() && versionName.length <= 50) { "Invalid version name" }
        val current = latest()
        require(current == null || versionCode > current.versionCode) {
            "Version $versionCode is not newer than ${current?.versionCode}"
        }
        directory.mkdirs()
        val release = AndroidRelease(
            versionCode = versionCode,
            versionName = versionName.trim(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) },
            sizeBytes = apk.size.toLong(),
            publishedAt = now,
        )
        // Write to a temporary file and move it into place, so a reader never sees half a file.
        atomicWrite(apk(release)) { it.writeBytes(apk) }
        atomicWrite(latestFile) { it.writeText(HabitaJson.encodeToString(AndroidRelease.serializer(), release)) }
        directory.listFiles { file -> file.name.matches(Regex("""habita-\d+\.apk""")) }
            .orEmpty()
            .sortedByDescending { it.name.removePrefix("habita-").removeSuffix(".apk").toInt() }
            .drop(KEEP)
            .forEach { it.delete() }
        return release
    }

    private fun atomicWrite(target: File, write: (File) -> Unit) {
        val temp = File(directory, ".${target.name}.tmp")
        write(temp)
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private companion object {
        const val KEEP = 3
    }
}