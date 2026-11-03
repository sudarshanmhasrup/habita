package dev.habita.app.feature.gym.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

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