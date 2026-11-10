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
import platform.UIKit.UIPasteboard
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.darwin.NSObject
import dev.habita.app.core.data.nowMillis
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

/** The full-strength system buzz; iOS has no custom patterns without Core Haptics. */
@Composable
internal actual fun rememberRestAlert(): () -> Unit = remember {
    { AudioServicesPlaySystemSound(kSystemSoundID_Vibrate) }
}

/** A local notification with the default sound; it asks for permission on first use. */
@Composable
internal actual fun rememberRestAlarm(): RestAlarm = remember {
    object : RestAlarm {
        private val id = "rest-end"

        override fun set(endsAt: Long) {
            val seconds = (endsAt - nowMillis()) / 1000.0
            if (seconds < 1) return
            val center = UNUserNotificationCenter.currentNotificationCenter()
            center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ -> }
            val content = UNMutableNotificationContent().apply {
                setTitle("Rest is over")
                setBody("Time for the next set.")
                setSound(UNNotificationSound.defaultSound)
            }
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
            center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(id, content, trigger), null)
        }

        override fun cancel() {
            val center = UNUserNotificationCenter.currentNotificationCenter()
            center.removePendingNotificationRequestsWithIdentifiers(listOf(id))
            center.removeDeliveredNotificationsWithIdentifiers(listOf(id))
        }
    }
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

@Composable
internal actual fun rememberTextCopier(): (String) -> Unit = remember {
    { text -> UIPasteboard.generalPasteboard.string = text }
}

/** Opens the Files picker for a JSON file and reads a copy of it. */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun rememberFileOpener(onResult: (String?) -> Unit): () -> Unit {
    val viewController = LocalUIViewController.current
    // The picker holds its delegate weakly, so it is kept here for as long as the screen is.
    val delegate = remember { FileOpenerDelegate() }
    delegate.onResult = onResult
    return remember(viewController) {
        {
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON), asCopy = true)
            picker.delegate = delegate
            viewController.presentViewController(picker, animated = true, completion = null)
        }
    }
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private class FileOpenerDelegate : NSObject(), UIDocumentPickerDelegateProtocol {
    var onResult: (String?) -> Unit = {}

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        onResult(url?.let { NSString.create(contentsOfURL = it, encoding = NSUTF8StringEncoding, error = null)?.toString() })
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onResult(null)
    }
}

/** iOS asks once at launch, when the reminders are queued. */
@Composable
internal actual fun rememberNotificationAccess(): NotificationAccess? = null