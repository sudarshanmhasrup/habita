package dev.habita.app.feature.gym.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import dev.habita.app.core.data.nowMillis
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Timer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import kotlin.concurrent.timerTask
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/** A beeping alarm through the speakers; desktop has no vibration. */
@Composable
internal actual fun rememberRestRinger(): RestRinger = DesktopRinger

/** Desktop rings with its own beep; there are no system alarm sounds to pick from. */
@Composable
internal actual fun rememberAlarmTone(): AlarmTone? = null

/** One beeping alarm for the whole app, looping until stopped or the cap runs out. */
private object DesktopRinger : RestRinger {
    private val state = RingState()
    private var clip: Clip? = null
    private var timer: Timer? = null

    override val ringing: StateFlow<Boolean> get() = state.ringing

    @Synchronized
    override fun start() {
        if (!state.start(nowMillis())) return
        // No sound device, such as on a headless machine: the screen still shows Dismiss.
        clip = runCatching {
            AudioSystem.getClip().apply {
                val format = AudioFormat(SAMPLE_RATE, 16, 1, true, false)
                val data = beeps()
                open(AudioInputStream(ByteArrayInputStream(data), format, data.size / 2L))
                loop(Clip.LOOP_CONTINUOUSLY)
            }
        }.getOrNull()
        timer = Timer("rest-alarm", true).apply {
            schedule(timerTask { expire() }, state.remainingMillis(nowMillis()))
        }
    }

    @Synchronized
    override fun stop() {
        if (state.stop()) silence()
    }

    @Synchronized
    private fun expire() {
        if (state.expire(nowMillis())) silence()
    }

    private fun silence() {
        timer?.cancel()
        timer = null
        clip?.run {
            stop()
            close()
        }
        clip = null
    }

    private const val SAMPLE_RATE = 44_100f

    /** Two short high beeps, then a pause: one second, 16-bit mono, little-endian. */
    private fun beeps(): ByteArray {
        val samples = SAMPLE_RATE.toInt()
        val bytes = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val t = i / SAMPLE_RATE
            val on = t < 0.15f || (t >= 0.25f && t < 0.4f)
            val value = if (on) (sin(2 * PI * 880 * t) * Short.MAX_VALUE * 0.6).toInt() else 0
            bytes[i * 2] = value.toByte()
            bytes[i * 2 + 1] = (value shr 8).toByte()
        }
        return bytes
    }
}

/** A desktop app keeps running in the background, so its own timer is enough. */
@Composable
internal actual fun rememberRestAlarm(): RestAlarm = remember {
    object : RestAlarm {
        override fun set(endsAt: Long) = Unit

        override fun cancel() = Unit
    }
}

@Composable
internal actual fun rememberFileSaver(onResult: (Boolean?) -> Unit): (fileName: String, contents: String) -> Unit =
    { fileName, contents ->
        val dialog = FileDialog(null as Frame?, "Save backup", FileDialog.SAVE).apply {
            file = fileName
            isVisible = true
        }
        val directory = dialog.directory
        val name = dialog.file
        if (directory == null || name == null) {
            onResult(null)
        } else {
            onResult(runCatching { File(directory, name).writeText(contents) }.isSuccess)
        }
    }

@Composable
internal actual fun rememberTextCopier(): (String) -> Unit = remember {
    { text -> Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
}

@Composable
internal actual fun rememberFileOpener(onResult: (String?) -> Unit): () -> Unit = {
    val dialog = FileDialog(null as Frame?, "Restore a backup", FileDialog.LOAD).apply { isVisible = true }
    val directory = dialog.directory
    val name = dialog.file
    onResult(if (directory == null || name == null) null else runCatching { File(directory, name).readText() }.getOrNull())
}

/** Desktop has no Sunday reminder to allow. */
@Composable
internal actual fun rememberNotificationAccess(): NotificationAccess? = null

/** Desktop has no camera to open; a photo comes from a file. */
@Composable
internal actual fun rememberPhotoSource(onPhoto: (ImageBitmap) -> Unit, onError: () -> Unit): PhotoSource? = remember {
    PhotoSource(takePhoto = null) {
        val dialog = FileDialog(null as Frame?, "Choose a photo", FileDialog.LOAD).apply { isVisible = true }
        val directory = dialog.directory
        val name = dialog.file
        if (directory != null && name != null) {
            val photo = runCatching { Image.makeFromEncoded(File(directory, name).readBytes()).toComposeImageBitmap() }.getOrNull()
            if (photo != null) onPhoto(photo) else onError()
        }
    }
}

/** Desktop has no share sheet: the card is saved as a PNG wherever the user picks. */
@Composable
internal actual fun rememberImageSharer(): ImageSharer? = remember {
    ImageSharer { image, fileName ->
        val dialog = FileDialog(null as Frame?, "Save photo card", FileDialog.SAVE).apply {
            file = fileName.substringBeforeLast('.') + ".png"
            isVisible = true
        }
        val directory = dialog.directory
        val name = dialog.file
        if (directory == null || name == null) {
            true
        } else {
            runCatching {
                val png = Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!
                File(directory, name).writeBytes(png.bytes)
            }.isSuccess
        }
    }
}