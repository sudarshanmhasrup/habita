package dev.habita.app.feature.expense.ui

import androidx.compose.runtime.Composable
import dev.habita.app.feature.expense.data.UpiStatus

/** An SMS from the phone's inbox. */
data class InboxSms(val sender: String?, val body: String, val receivedAt: Long)

/**
 * Access to bank SMS. [supported] is false where the platform can't read SMS (desktop). With
 * [granted], new bank SMS are read as they arrive and [inbox] can catch up on older ones.
 */
interface SmsAccess {
    val supported: Boolean
    val granted: Boolean

    /** Whether the app may open itself on a new payment ("display over other apps" on Android). */
    val canOpenOnPayment: Boolean

    /** Asks for SMS and notification permission. */
    fun request()

    /** Opens the setting that lets the app open itself on a new payment. */
    fun requestOpenOnPayment()

    /** Every SMS received since [since], oldest first. Empty without permission. */
    suspend fun inbox(since: Long): List<InboxSms>
}

@Composable
internal expect fun rememberSmsAccess(): SmsAccess

/** Scanning UPI QR codes and handing payments to a UPI app (GPay, PhonePe, …). */
interface UpiAccess {
    /** False where there are no UPI apps (desktop). */
    val supported: Boolean

    /** Opens the QR scanner; [onResult] gets the QR's text, or `null` when backed out. */
    fun scan(onResult: (String?) -> Unit)

    /**
     * Opens a UPI app with the payment [uri]. [onResult] gets what the app reported, or `null`
     * when no UPI app is installed.
     */
    fun pay(uri: String, onResult: (UpiStatus?) -> Unit)
}

@Composable
internal expect fun rememberUpi(): UpiAccess

/**
 * Returns a function that asks where to save a file named `fileName`, then writes the contents
 * it is given there. [onResult] reports whether the file was saved; `null` means the user backed
 * out.
 */
@Composable
internal expect fun rememberFileSaver(onResult: (Boolean?) -> Unit): (fileName: String, contents: String) -> Unit