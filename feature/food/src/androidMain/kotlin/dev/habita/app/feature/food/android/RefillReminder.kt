package dev.habita.app.feature.food.android

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dev.habita.app.feature.food.FoodFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/** Implemented by the app's [android.app.Application], so the refill question can reach food. */
interface FoodHost {
    val food: FoodFeature

    /** Opens the app; the notification adds [OPEN_REFILL_EXTRA] to land on the refill. */
    fun launchIntent(context: Context): Intent

    /** A one-colour icon for the status bar. */
    val notificationIcon: Int
}

/** Set on the launch intent when the refill notification is tapped. */
const val OPEN_REFILL_EXTRA = "dev.habita.app.open_refill"

/**
 * Asks once a day, at 9:00, whether to refill when an app-bought item is about to run out. It
 * only asks: tapping it opens the refill, and "Not now" waits until tomorrow. Like the waist
 * reminder, it runs on the phone's alarm, with no server or push service, and may come a few
 * minutes late so Android can save battery.
 */
object RefillReminder {
    private const val CHANNEL = "refill"
    private const val NOTIFICATION_ID = 8
    private const val HOUR = 9
    private const val WINDOW_MILLIS = 30L * 60 * 1000

    /** Sets the alarm for the next 9:00. Safe to call again: it replaces the last one. */
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.setWindow(AlarmManager.RTC_WAKEUP, nextMorning(System.currentTimeMillis()), WINDOW_MILLIS, alarm(context))
    }

    /** The next 9:00 after [now], in the phone's time zone. */
    internal fun nextMorning(now: Long): Long {
        val at = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (at.timeInMillis <= now) at.add(Calendar.DAY_OF_YEAR, 1)
        return at.timeInMillis
    }

    internal fun show(context: Context, host: FoodHost, question: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Refills", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Asks before groceries run out"
                },
            )
        }
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            host.launchIntent(context).putExtra(OPEN_REFILL_EXTRA, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notNow = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID,
            Intent(context, RefillSnoozeReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
        manager.notify(
            NOTIFICATION_ID,
            builder
                .setSmallIcon(host.notificationIcon)
                .setContentTitle("Time to refill?")
                .setContentText(question)
                .setStyle(Notification.BigTextStyle().bigText(question))
                .setContentIntent(open)
                .addAction(Notification.Action.Builder(null, "Not now", notNow).build())
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build(),
        )
    }

    internal fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun alarm(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        NOTIFICATION_ID,
        Intent(context, RefillReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private val refillScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** The 9:00 alarm: asks about a refill when one is due, then sets tomorrow's. */
class RefillReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val host = context.applicationContext as? FoodHost ?: return
        val pending = goAsync()
        refillScope.launch {
            try {
                host.food.refillQuestionNow()?.let { RefillReminder.show(context, host, it) }
            } finally {
                RefillReminder.schedule(context)
                pending.finish()
            }
        }
    }
}

/** "Not now" on the notification: no refill questions until tomorrow. */
class RefillSnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val host = context.applicationContext as? FoodHost ?: return
        RefillReminder.dismiss(context)
        val pending = goAsync()
        refillScope.launch {
            try {
                host.food.snoozeRefill()
            } finally {
                pending.finish()
            }
        }
    }
}