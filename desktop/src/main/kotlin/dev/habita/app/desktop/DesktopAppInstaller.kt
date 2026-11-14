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
 * - Mac: the new Habita replaces the running one where it's installed (never moved to
 *   Applications), then opens again.
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
        DesktopSystem.MAC -> "Installing. Habita opens again when it's done."
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
                DesktopSystem.MAC -> installOnMac(build)
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

    /**
     * Replaces the running Habita.app in place, wherever it's installed, so an app kept outside
     * Applications stays there. A small script waits for Habita to close, copies the new app out of
     * the disk image next to the old one, swaps them, and opens it. If it can't tell where Habita is
     * installed (run from source, for example), it opens the disk image instead.
     */
    private fun installOnMac(dmg: File) {
        val bundle = runningApp()?.let { macBundle(it) }
        if (bundle == null) {
            ProcessBuilder("open", dmg.absolutePath).start()
            quit()
            return
        }
        val script = File(dmg.parentFile, "install.sh")
        script.writeText(macInstallScript(ProcessHandle.current().pid(), dmg.absolutePath, bundle))
        ProcessBuilder("/usr/bin/nohup", "/bin/bash", script.absolutePath)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
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

/** The `.app` folder that holds [launcher], such as `/x/Habita.app/Contents/MacOS/Habita`. */
internal fun macBundle(launcher: String): String? {
    val end = launcher.indexOf(".app/Contents/MacOS/")
    return if (end < 0) null else launcher.substring(0, end + ".app".length)
}

/**
 * The script that swaps in the new app: it waits for [pid] to exit, copies Habita.app out of
 * [dmg] next to [bundle], replaces [bundle] with it, and opens it. A failed copy leaves the old
 * app as it was and opens the disk image instead.
 */
internal fun macInstallScript(pid: Long, dmg: String, bundle: String): String {
    fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    return listOf(
        "#!/bin/bash",
        "dmg=${quote(dmg)}",
        "dest=${quote(bundle)}",
        "while kill -0 $pid 2>/dev/null; do sleep 0.5; done",
        "mnt=\$(mktemp -d)",
        "if hdiutil attach -nobrowse -readonly -mountpoint \"\$mnt\" \"\$dmg\" >/dev/null && " +
            "rm -rf \"\$dest.new\" && ditto \"\$mnt/Habita.app\" \"\$dest.new\"; then",
        "  hdiutil detach -quiet \"\$mnt\"",
        "  rm -rf \"\$dest\" && mv \"\$dest.new\" \"\$dest\"",
        "  xattr -dr com.apple.quarantine \"\$dest\" 2>/dev/null",
        "  rm -f \"\$dmg\"",
        "  open \"\$dest\"",
        "else",
        "  hdiutil detach -quiet \"\$mnt\" 2>/dev/null",
        "  rm -rf \"\$dest.new\"",
        "  open \"\$dmg\"",
        "fi",
        "rm -f \"\$0\"",
    ).joinToString("\n")
}