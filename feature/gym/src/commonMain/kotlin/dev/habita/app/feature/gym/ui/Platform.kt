package dev.habita.app.feature.gym.ui

import androidx.compose.runtime.Composable

/** Returns a function that buzzes the phone, used when a rest ends. */
@Composable
internal expect fun rememberRestAlert(): () -> Unit

/**
 * The end of a rest while the app is away, such as with the phone locked: the system rings and
 * buzzes then, since the app's own timer stops running in the background.
 */
internal interface RestAlarm {
    /** Rings at [endsAt]; replaces any rest alarm set before. */
    fun set(endsAt: Long)

    /** Takes back the alarm, and its notification if it already rang. */
    fun cancel()
}

@Composable
internal expect fun rememberRestAlarm(): RestAlarm

/**
 * Returns a function that asks where to save a file named `fileName`, then writes the contents
 * it is given there. [onResult] reports whether the file was saved; `null` means the user backed
 * out.
 */
@Composable
internal expect fun rememberFileSaver(onResult: (Boolean?) -> Unit): (fileName: String, contents: String) -> Unit

/** Returns a function that puts text on the clipboard, for pasting into another app. */
@Composable
internal expect fun rememberTextCopier(): (String) -> Unit

/**
 * Returns a function that asks for a file to open, then hands [onResult] its text, or `null` when
 * the user backed out or the file couldn't be read.
 */
@Composable
internal expect fun rememberFileOpener(onResult: (String?) -> Unit): () -> Unit

/** Whether the app may show notifications, and a way to ask. */
internal class NotificationAccess(val enabled: Boolean, val request: () -> Unit)

/**
 * Notification access for the Sunday reminder, rechecked when the app comes back. `null` where the
 * gym has nothing to ask: iOS asks once at launch, and desktop has no reminder.
 */
@Composable
internal expect fun rememberNotificationAccess(): NotificationAccess?