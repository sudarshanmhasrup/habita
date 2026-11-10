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
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/** Desktop has no vibration. */
@Composable
internal actual fun rememberRestAlert(): () -> Unit = {}

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