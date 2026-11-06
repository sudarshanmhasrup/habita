package dev.habita.app.feature.gym.ui

import android.Manifest
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
internal actual fun rememberRestAlert(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            // Two firm pulses: noticeable through a pocket, short enough not to annoy.
            val pattern = longArrayOf(0, 250, 150, 250)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }
    }
}

@Composable
internal actual fun rememberFileSaver(onResult: (Boolean?) -> Unit): (fileName: String, contents: String) -> Unit {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<String>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val contents = pending[0]
        pending[0] = null
        if (uri == null || contents == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        val saved = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(contents.toByteArray()) }
        }.isSuccess
        onResult(saved)
    }
    return remember(launcher) {
        { fileName, contents ->
            pending[0] = contents
            launcher.launch(fileName)
        }
    }
}

@Composable
internal actual fun rememberTextCopier(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Habita check-in", text))
        }
    }
}

@Composable
internal actual fun rememberFileOpener(onResult: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onResult(uri?.let { runCatching { context.contentResolver.openInputStream(it)!!.use { stream -> stream.readBytes().decodeToString() } }.getOrNull() })
    }
    // Any type: cloud drives don't always label a backup as JSON. The contents are checked instead.
    return remember(launcher) { { launcher.launch(arrayOf("*/*")) } }
}

@Composable
internal actual fun rememberNotificationAccess(): NotificationAccess? {
    val context = LocalContext.current
    fun enabled(): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }
    var on by remember { mutableStateOf(enabled()) }
    // After one "Don't allow", Android stops showing the prompt, so later taps open the settings.
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        asked = true
        on = enabled()
    }
    // The user can change it in the settings while the app is away.
    LifecycleResumeEffect(Unit) {
        on = enabled()
        onPauseOrDispose { }
    }
    return NotificationAccess(on) {
        val canPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (canPrompt) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}