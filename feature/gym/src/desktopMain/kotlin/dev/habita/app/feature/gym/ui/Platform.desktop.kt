package dev.habita.app.feature.gym.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

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