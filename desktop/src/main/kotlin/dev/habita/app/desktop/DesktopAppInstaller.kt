package dev.habita.app.desktop

import dev.habita.app.core.update.AppInstaller
import dev.habita.app.core.update.UpdateException
import dev.habita.app.shared.api.AppRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** The desktop systems Habita publishes builds for, by the server's name for them. */
internal enum class DesktopSystem(val id: String, val extension: String) {
    WINDOWS("windows", "msi"),
    MAC("mac", "dmg"),
    LINUX("linux", "deb"),
    ;

    companion object {
        fun current(): DesktopSystem? {
            val name = System.getProperty("os.name").orEmpty().lowercase()
            return when {
                name.startsWith("windows") -> WINDOWS
                name.startsWith("mac") -> MAC
                name.startsWith("linux") -> LINUX
                else -> null
            }
        }
    }
}

/**
 * Installs updates for an installed desktop app. The build is checked against the server's
 * SHA-256 before anything runs it. Data lives in the user's `.habita` folder, which no install
 * touches.
 *
 * - Windows: the installer runs on its own with a progress bar, then Habita opens again.
 * - Mac: the disk image opens for Habita to be dragged into Applications, as on first install.
 * - Linux: the system asks for the user's password, installs the package, then Habita opens again.
 *
 * [quit] closes the app, so the install can replace it.
 */
internal class DesktopAppInstaller(
    private val system: DesktopSystem,
    override val versionCode: Int,
    private val quit: () -> Unit,
) : AppInstaller {
    override val platform = system.id

    override val installingHint = when (system) {
        DesktopSystem.WINDOWS -> "Installing. Habita opens again when it's done."
        DesktopSystem.MAC -> "Drag Habita into Applications, replace the old one, then open it."
        DesktopSystem.LINUX -> "Enter your password to install. Habita opens again when it's done."
    }

    override suspend fun install(
        release: AppRelease,
        download: suspend (onProgress: (Float) -> Unit, write: (ByteArray, Int) -> Unit) -> Unit,
        onProgress: (Float) -> Unit,
    ) {
        val build = withContext(Dispatchers.IO) { downloadChecked(release, download, onProgress) }
        withContext(Dispatchers.IO) {
            when (system) {
                DesktopSystem.WINDOWS -> installOnWindows(build)
                DesktopSystem.MAC -> {
                    ProcessBuilder("open", build.absolutePath).start()
                    quit()
                }
                DesktopSystem.LINUX -> installOnLinux(build)
            }
        }
    }

    private suspend fun downloadChecked(
        release: AppRelease,
        download: suspend (onProgress: (Float) -> Unit, write: (ByteArray, Int) -> Unit) -> Unit,
        onProgress: (Float) -> Unit,
    ): File {
        val folder = File(System.getProperty("java.io.tmpdir"), "habita-updates").apply { mkdirs() }
        folder.listFiles()?.forEach { it.delete() }
        val file = File(folder, "habita-${release.versionCode}.${system.extension}")
        val digest = MessageDigest.getInstance("SHA-256")
        file.outputStream().use { out ->
            download(onProgress) { bytes, count ->
                out.write(bytes, 0, count)
                digest.update(bytes, 0, count)
            }
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        if (sha256 != release.sha256 || file.length() != release.sizeBytes) {
            file.delete()
            throw UpdateException("The download was damaged. Tap Try again.")
        }
        return file
    }

    /**
     * A small script waits for Habita to close, runs the installer with only a progress bar, and
     * opens the new Habita. It runs on its own, so it carries on after Habita quits.
     */
    private fun installOnWindows(msi: File) {
        val app = runningApp()
        val script = File(msi.parentFile, "install.cmd")
        script.writeText(
            listOf(
                "@echo off",
                "timeout /t 2 /nobreak >nul",
                "msiexec /i \"${msi.absolutePath}\" /passive /norestart",
                if (app != null) "start \"\" \"$app\"" else "",
                "del \"${msi.absolutePath}\"",
                "(goto) 2>nul & del \"%~f0\"",
            ).filter { it.isNotEmpty() }.joinToString("\r\n"),
        )
        ProcessBuilder("cmd.exe", "/c", "start", "", "/min", script.absolutePath).start()
        quit()
    }

    /** Installs with the system's password prompt, then opens the new Habita. */
    private fun installOnLinux(deb: File) {
        val result = try {
            ProcessBuilder("pkexec", "dpkg", "-i", deb.absolutePath).inheritIO().start().waitFor()
        } catch (_: Exception) {
            throw UpdateException("Couldn't start the installer. Get the new build from the download page.")
        }
        // pkexec returns 126 when the password prompt is dismissed.
        if (result == 126 || result == 127) throw UpdateException("The install was cancelled. Tap Try again.")
        if (result != 0) throw UpdateException("The install failed. Get the new build from the download page.")
        deb.delete()
        runningApp()?.let { ProcessBuilder(it).start() }
        quit()
    }

    /** The installed app's launcher, which opens the new build once it's in place. */
    private fun runningApp(): String? = ProcessHandle.current().info().command().orElse(null)
}