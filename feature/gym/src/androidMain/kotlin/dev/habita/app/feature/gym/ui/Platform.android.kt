package dev.habita.app.feature.gym.ui

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.habita.app.feature.gym.android.RestEndAlarm
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberRestRinger(): RestRinger {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        object : RestRinger {
            override val ringing: StateFlow<Boolean> = RestEndAlarm.ringing

            override fun start() = RestEndAlarm.ring(context)

            override fun stop() = RestEndAlarm.stop(context)
        }
    }
}

/** The system sound picker, listing the phone's alarm sounds with its default alarm first. */
@Composable
internal actual fun rememberAlarmTone(): AlarmTone? {
    val context = LocalContext.current.applicationContext
    var name by remember(context) { mutableStateOf(RestEndAlarm.toneName(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        @Suppress("DEPRECATION")
        val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (result.resultCode == Activity.RESULT_OK && picked != null) {
            RestEndAlarm.setTone(context, picked)
            name = RestEndAlarm.toneName(context)
        }
    }
    return AlarmTone(name) {
        val current = RestEndAlarm.tone(context) ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        launcher.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Rest alarm")
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current),
        )
    }
}

@Composable
internal actual fun rememberRestAlarm(): RestAlarm {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        object : RestAlarm {
            override fun set(endsAt: Long) = RestEndAlarm.set(context, endsAt)

            override fun cancel() = RestEndAlarm.cancel(context)
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
    fun openSettings() = context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        on = enabled()
        // After the second "Don't allow", Android answers no without asking: the only way left is
        // the app's notification settings, so go there instead of seeming to do nothing.
        val asksAgain = context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
        if (!granted && !asksAgain) openSettings()
    }
    // The user can change it in the settings while the app is away.
    LifecycleResumeEffect(Unit) {
        on = enabled()
        onPauseOrDispose { }
    }
    return NotificationAccess(on) {
        // The permission prompt when Android still shows one; otherwise, or when notifications are
        // switched off in the settings, the settings.
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else openSettings()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Photos are decoded no larger than this on their long side: plenty for a 1080 × 1920 card. */
private const val PHOTO_MAX_EDGE = 2400

private fun Context.fileUri(file: File): Uri = FileProvider.getUriForFile(this, "$packageName.files", file)

/** Decodes [uri] upright (ImageDecoder applies the camera's rotation) and scaled down to fit. */
private fun Context.decodePhoto(uri: Uri): ImageBitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
        // A software bitmap, so the card can draw it onto its own canvas.
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longEdge = maxOf(info.size.width, info.size.height)
        if (longEdge > PHOTO_MAX_EDGE) {
            val scale = PHOTO_MAX_EDGE.toFloat() / longEdge
            decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
        }
    }.asImageBitmap()

@Composable
internal actual fun rememberPhotoSource(onPhoto: (ImageBitmap) -> Unit, onError: () -> Unit): PhotoSource? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun load(uri: Uri) {
        scope.launch {
            val photo = withContext(Dispatchers.IO) { runCatching { context.decodePhoto(uri) }.getOrNull() }
            if (photo != null) onPhoto(photo) else onError()
        }
    }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(::load)
    }
    // The camera app writes the photo to this file in the app's cache; the same file every time.
    val cameraFile = remember(context) { File(File(context.cacheDir, "camera"), "photo.jpg") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) load(Uri.fromFile(cameraFile))
    }
    return remember(chooser, camera) {
        PhotoSource(
            takePhoto = {
                cameraFile.parentFile?.mkdirs()
                runCatching { camera.launch(context.fileUri(cameraFile)) }.onFailure { onError() }
            },
            choosePhoto = {
                chooser.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }
}

@Composable
internal actual fun rememberImageSharer(): ImageSharer? {
    val context = LocalContext.current
    return remember(context) {
        ImageSharer { image, fileName ->
            runCatching {
                // Only the latest card is kept; older ones are cleared so the cache doesn't grow.
                val folder = File(context.cacheDir, "share").apply {
                    mkdirs()
                    listFiles()?.forEach { it.delete() }
                }
                val file = File(folder, fileName)
                file.outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, 92, it) }
                val uri = context.fileUri(file)
                val send = Intent(Intent.ACTION_SEND)
                    .setType("image/jpeg")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // Lets the share sheet show the card as its preview.
                send.clipData = ClipData.newRawUri(null, uri)
                context.startActivity(Intent.createChooser(send, null))
            }.isSuccess
        }
    }
}