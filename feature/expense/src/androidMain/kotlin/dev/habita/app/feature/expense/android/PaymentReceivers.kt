package dev.habita.app.feature.expense.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.provider.Telephony
import android.view.View
import android.view.WindowManager
import dev.habita.app.feature.expense.ExpenseFeature
import dev.habita.app.feature.expense.data.Category
import dev.habita.app.feature.expense.data.SmsOutcome
import dev.habita.app.feature.expense.data.formatRupees
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Implemented by the app's [android.app.Application], so the receivers can reach the feature. */
interface ExpenseHost {
    val expense: ExpenseFeature

    /** Opens the app; the receivers add which payment to show. */
    fun launchIntent(context: Context): Intent

    /** A one-colour icon for the status bar. */
    val notificationIcon: Int
}

/** On the intent that opens the app from a payment notification: the payment to show. */
const val OPEN_EXPENSE_EXTRA = "dev.habita.app.open_expense"

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Runs [block] off the main thread, keeping the receiver alive until it finishes. */
private fun BroadcastReceiver.work(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/**
 * Reads each incoming SMS. A bank debit becomes a payment, and a notification asks for its
 * category, or says which one it went to when the payee was seen before.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val host = context.applicationContext as? ExpenseHost ?: return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // A long SMS arrives in parts; join them per sender.
        val messages = parts.filterNotNull().groupBy { it.originatingAddress }.map { (sender, pieces) ->
            Triple(sender, pieces.joinToString("") { it.messageBody.orEmpty() }, pieces.first().timestampMillis)
        }
        work {
            messages.forEach { (sender, body, receivedAt) ->
                val outcome = host.expense.recordSms(body, sender, receivedAt) as? SmsOutcome.Recorded ?: return@forEach
                val choices = if (outcome.category == null) host.expense.quickCategories(2) else emptyList()
                PaymentNotifications.show(context, host, outcome, choices)
                // A payee seen for the first time: open the app on it, so it's recorded right away.
                if (outcome.category == null) PaymentLauncher.open(context, host, outcome.expense.id)
            }
        }
    }
}

/** Sorts a payment into the category tapped on its notification. */
class CategoryActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val host = context.applicationContext as? ExpenseHost ?: return
        val expenseId = intent.getStringExtra(EXTRA_EXPENSE) ?: return
        val categoryId = intent.getStringExtra(EXTRA_CATEGORY) ?: return
        work {
            host.expense.setCategory(expenseId, categoryId)
            PaymentNotifications.cancel(context, expenseId)
        }
    }

    internal companion object {
        const val EXTRA_EXPENSE = "expense_id"
        const val EXTRA_CATEGORY = "category_id"
    }
}

/**
 * Opens the app on a payment from the background. Android only allows that for apps the user has
 * let "display over other apps", and from Android 15 only while such an app shows a window, so a
 * tiny invisible one is added for the moment of the launch.
 */
internal object PaymentLauncher {
    fun open(context: Context, host: ExpenseHost, expenseId: String) {
        if (!Settings.canDrawOverlays(context)) return
        Handler(Looper.getMainLooper()).post {
            val windows = context.getSystemService(WindowManager::class.java)
            val marker = View(context)
            @Suppress("DEPRECATION")
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = WindowManager.LayoutParams(
                1,
                1,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            )
            val added = runCatching { windows.addView(marker, params) }.isSuccess
            runCatching {
                context.startActivity(host.launchIntent(context).putExtra(OPEN_EXPENSE_EXTRA, expenseId))
            }
            if (added) Handler(Looper.getMainLooper()).postDelayed({ runCatching { windows.removeView(marker) } }, 2_000)
        }
    }
}

internal object PaymentNotifications {
    private const val CHANNEL = "payments"

    fun show(context: Context, host: ExpenseHost, outcome: SmsOutcome.Recorded, choices: List<Category>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Payments", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A note for each bank payment, to pick its category"
                },
            )
        }
        val expense = outcome.expense
        val id = expense.id.hashCode()
        val open = PendingIntent.getActivity(
            context,
            id,
            host.launchIntent(context).putExtra(OPEN_EXPENSE_EXTRA, expense.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        @Suppress("DEPRECATION")
        val builder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL) else Notification.Builder(context))
            .setSmallIcon(host.notificationIcon)
            .setContentTitle("${formatRupees(expense.amountPaise)} · ${expense.description}")
            .setContentText(outcome.category?.let { "Saved in ${it.name}" } ?: "Tap a category, or open to pick another")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
        if (outcome.category == null) {
            choices.forEachIndexed { index, category ->
                val tap = PendingIntent.getBroadcast(
                    context,
                    id * 4 + index,
                    Intent(context, CategoryActionReceiver::class.java)
                        .putExtra(CategoryActionReceiver.EXTRA_EXPENSE, expense.id)
                        .putExtra(CategoryActionReceiver.EXTRA_CATEGORY, category.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(action(category.name, tap))
            }
            builder.addAction(action("Other…", open))
        } else {
            builder.addAction(action("Change", open))
        }
        manager.notify(expense.id, id, builder.build())
    }

    private fun action(title: String, intent: PendingIntent): Notification.Action =
        Notification.Action.Builder(null as Icon?, title, intent).build()

    fun cancel(context: Context, expenseId: String) {
        context.getSystemService(NotificationManager::class.java).cancel(expenseId, expenseId.hashCode())
    }
}