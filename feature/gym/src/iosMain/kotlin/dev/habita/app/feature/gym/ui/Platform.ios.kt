package dev.habita.app.feature.gym.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.AudioToolbox.kSystemSoundID_Vibrate
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject

/** The full-strength system buzz; iOS has no custom patterns without Core Haptics. */
@Composable
internal actual fun rememberRestAlert(): () -> Unit = remember {
    { AudioServicesPlaySystemSound(kSystemSoundID_Vibrate) }
}

/**
 * Writes the file to the temporary folder, then opens the "Save to Files" sheet so the user picks
 * where the copy goes.
 */
@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
@Composable
internal actual fun rememberFileSaver(onResult: (Boolean?) -> Unit): (fileName: String, contents: String) -> Unit {
    val viewController = LocalUIViewController.current
    // The picker holds its delegate weakly, so it is kept here for as long as the screen is.
    val delegate = remember { FileSaverDelegate() }
    delegate.onResult = onResult
    return remember(viewController) {
        { fileName, contents ->
            val url = NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByAppendingPathComponent(fileName)
            val written = url != null && NSString.create(string = contents)
                .writeToURL(url, atomically = true, encoding = NSUTF8StringEncoding, error = null)
            if (!written) {
                delegate.onResult(false)
            } else {
                delegate.temporaryFile = url
                val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
                picker.delegate = delegate
                viewController.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class FileSaverDelegate : NSObject(), UIDocumentPickerDelegateProtocol {
    var onResult: (Boolean?) -> Unit = {}
    var temporaryFile: NSURL? = null

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        finish(true)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        finish(null)
    }

    private fun finish(result: Boolean?) {
        temporaryFile?.let { NSFileManager.defaultManager.removeItemAtURL(it, error = null) }
        temporaryFile = null
        onResult(result)
    }
}