package dev.habita.app.feature.expense.ui

import androidx.compose.runtime.Composable
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Desktop can't read SMS; payments are added by hand. */
@Composable
internal actual fun rememberSmsAccess(): SmsAccess = object : SmsAccess {
    override val supported = false
    override val granted = false
    override val canOpenOnPayment = false

    override fun request() = Unit

    override fun requestOpenOnPayment() = Unit

    override suspend fun inbox(since: Long): List<InboxSms> = emptyList()
}

@Composable
internal actual fun rememberFileSaver(onResult: (Boolean?) -> Unit): (fileName: String, contents: String) -> Unit =
    { fileName, contents ->
        val dialog = FileDialog(null as Frame?, "Save month", FileDialog.SAVE).apply {
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