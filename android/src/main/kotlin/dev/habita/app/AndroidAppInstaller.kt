package dev.habita.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import dev.habita.app.core.update.AppInstaller
import dev.habita.app.core.update.UpdateException
import dev.habita.app.shared.api.AppRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Installs updates from the Habita server. The APK is checked against the server's SHA-256 before
 * the system installer sees it, and Android itself checks it is signed with Habita's key, so a
 * tampered file can't be installed.
 */
class AndroidAppInstaller(private val context: Context) : AppInstaller {
    override val versionCode: Int = BuildConfig.VERSION_CODE
    override val platform = "android"
    override val installingHint = "Confirm the install when Android asks."

    override suspend fun install(
        release: AppRelease,
        download: suspend (onProgress: (Float) -> Unit, write: (ByteArray, Int) -> Unit) -> Unit,
        onProgress: (Float) -> Unit,
    ) {
        val packageManager = context.packageManager
        if (!packageManager.canRequestPackageInstalls()) {
            // Android asks once whether Habita may install apps; the toggle is in this screen.
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            throw UpdateException("Allow Habita to install updates, then tap Install again.")
        }

        val apk = withContext(Dispatchers.IO) {
            val folder = File(context.cacheDir, "updates").apply { mkdirs() }
            folder.listFiles()?.forEach { it.delete() }
            val file = File(folder, "habita-${release.versionCode}.apk")
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
                throw UpdateException("The download was damaged. Tap Install to try again.")
            }
            file
        }

        withContext(Dispatchers.IO) {
            val installer = packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("habita.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val callback = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(context, InstallResultReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(callback.intentSender)
            }
            apk.delete()
        }
    }
}

/** Shows the system's "Update this app?" screen when the installer asks for the user. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) != PackageInstaller.STATUS_PENDING_USER_ACTION) return
        @Suppress("DEPRECATION")
        val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
        context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}