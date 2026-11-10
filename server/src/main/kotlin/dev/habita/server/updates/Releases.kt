package dev.habita.server.updates

import dev.habita.app.shared.api.AppRelease
import dev.habita.server.HabitaJson
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/**
 * The platforms Habita publishes builds for, by the name used in their addresses
 * (`/app/<id>/...`), with the file each build is and the largest one accepted.
 */
enum class Platform(val id: String, val extension: String, val maxBytes: Long, private val looksRight: (File) -> Boolean) {
    // An APK is a zip.
    ANDROID("android", "apk", 100L * MB, { it.startsWith(byteArrayOf(0x50, 0x4B)) }),

    // An MSI is a Windows compound file.
    WINDOWS("windows", "msi", 300L * MB, { it.startsWith(byteArrayOf(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) }),

    // A disk image ends with a 512-byte trailer that starts with "koly".
    MAC("mac", "dmg", 300L * MB, { it.hasAt(it.length() - 512, "koly".encodeToByteArray()) }),

    // A Debian package is an ar archive.
    LINUX("linux", "deb", 300L * MB, { it.startsWith("!<arch>\n".encodeToByteArray()) }),
    ;

    /** Whether [file] is the kind of file this platform installs. */
    fun accepts(file: File): Boolean = runCatching { looksRight(file) }.getOrDefault(false)

    companion object {
        fun byId(id: String?): Platform? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Builds for one [platform] published by CI, kept as files in [directory]: the builds and
 * `latest.json` describing the newest. Only the newest [KEEP] builds stay.
 */
class Releases(private val directory: File, val platform: Platform) {
    private val latestFile = File(directory, "latest.json")
    private val lock = Any()

    /** Read without the lock: `latest.json` is only ever replaced whole, so it's never half written. */
    fun latest(): AppRelease? =
        latestFile.takeIf { it.isFile }?.let { runCatching { HabitaJson.decodeFromString<AppRelease>(it.readText()) }.getOrNull() }

    fun file(release: AppRelease): File = File(directory, "habita-${release.versionCode}.${platform.extension}")

    /** Saves [bytes] as the newest build; see the other [publish]. */
    fun publish(bytes: ByteArray, versionCode: Int, versionName: String, now: Long): AppRelease =
        publish(bytes.inputStream(), versionCode, versionName, now)

    /**
     * Saves the build read from [input] as the newest. It goes to disk as it arrives, so a large
     * desktop build never sits in memory. Refuses anything larger than the platform allows, that
     * isn't the platform's kind of file, or that isn't newer than the current build, so an old
     * build can never be offered as an update.
     */
    fun publish(input: InputStream, versionCode: Int, versionName: String, now: Long): AppRelease {
        require(versionName.isNotBlank() && versionName.length <= 50) { "Invalid version name" }
        directory.mkdirs()
        // Each upload gets its own temporary file, so two at once can't mix.
        val temp = File(directory, ".upload-${UUID.randomUUID()}.tmp")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            temp.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    size += read
                    require(size <= platform.maxBytes) { "The build is too large" }
                    digest.update(buffer, 0, read)
                    out.write(buffer, 0, read)
                }
            }
            require(platform.accepts(temp)) { "Not a ${platform.extension.uppercase()} file" }
            return synchronized(lock) {
                val current = latest()
                require(current == null || versionCode > current.versionCode) {
                    "Version $versionCode is not newer than ${current?.versionCode}"
                }
                val release = AppRelease(
                    versionCode = versionCode,
                    versionName = versionName.trim(),
                    sha256 = digest.digest().joinToString("") { "%02x".format(it) },
                    sizeBytes = size,
                    publishedAt = now,
                )
                // Moved into place whole, so a reader never sees half a file.
                move(temp, file(release))
                atomicWrite(latestFile) { it.writeText(HabitaJson.encodeToString(AppRelease.serializer(), release)) }
                val name = Regex("""habita-(\d+)\.${platform.extension}""")
                directory.listFiles { file -> file.name.matches(name) }
                    .orEmpty()
                    .sortedByDescending { name.matchEntire(it.name)!!.groupValues[1].toInt() }
                    .drop(KEEP)
                    .forEach { it.delete() }
                release
            }
        } finally {
            temp.delete()
        }
    }

    private fun atomicWrite(target: File, write: (File) -> Unit) {
        val temp = File(directory, ".${target.name}.tmp")
        write(temp)
        move(temp, target)
    }

    private fun move(from: File, to: File) {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private companion object {
        const val KEEP = 3
    }
}

/**
 * The release store for every platform. Android builds stay where they always were, at the top
 * of [directory]; each desktop platform gets its own folder.
 */
fun platformReleases(directory: File): Map<Platform, Releases> = Platform.entries.associateWith { platform ->
    Releases(if (platform == Platform.ANDROID) directory else File(directory, platform.id), platform)
}

private const val MB = 1024L * 1024

private fun byteArrayOf(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

private fun File.startsWith(prefix: ByteArray): Boolean = hasAt(0, prefix)

private fun File.hasAt(offset: Long, expected: ByteArray): Boolean {
    if (offset < 0 || length() < offset + expected.size) return false
    RandomAccessFile(this, "r").use { file ->
        file.seek(offset)
        val actual = ByteArray(expected.size)
        file.readFully(actual)
        return actual.contentEquals(expected)
    }
}