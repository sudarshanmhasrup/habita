package dev.habita.app.feature.gym.ui

import androidx.compose.runtime.Composable
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Desktop has no vibration. */
@Composable
internal actual fun rememberRestAlert(): () -> Unit = {}

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