package dev.habita.app.feature.gym.android

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
import dev.habita.app.feature.gym.GymFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/** Implemented by the app's [android.app.Application], so the reminder can reach the gym. */
interface GymHost {
    val gym: GymFeature

    /** Opens the app on the home screen, whose gym card shows the reminder too. */
    fun launchIntent(context: Context): Intent

    /** A one-colour icon for the status bar. */
    val notificationIcon: Int
}

/**
 * A Sunday morning nudge to measure the waist, skipped when it was already measured that week.
 * It runs on the phone with the system alarm, so it needs no server or push service. The alarm is
 * inexact, so Android can batch it with others to save battery; a few minutes late is fine.
 */
object WaistReminder {
    private const val CHANNEL = "body"
    private const val NOTIFICATION_ID = 7
    private const val HOUR = 9
    private const val WINDOW_MILLIS = 30L * 60 * 1000
    private const val TEXT = "At the belly button, relaxed, after breathing out. Then log it in the gym."

    /** Sets the alarm for the next Sunday at 9:00. Safe to call again: it replaces the last one. */
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.setWindow(AlarmManager.RTC_WAKEUP, nextSunday(System.currentTimeMillis()), WINDOW_MILLIS, alarm(context))
    }

    /** The next Sunday at 9:00 after [now], in the phone's time zone. */
    internal fun nextSunday(now: Long): Long {
        val at = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val days = (Calendar.SUNDAY - get(Calendar.DAY_OF_WEEK) + 7) % 7
            add(Calendar.DAY_OF_YEAR, days)
        }
        if (at.timeInMillis <= now) at.add(Calendar.DAY_OF_YEAR, 7)
        return at.timeInMillis
    }

    internal fun show(context: Context, host: GymHost) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Body measurements", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A Sunday reminder to measure your waist"
                },
            )
        }
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            host.launchIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
        manager.notify(
            NOTIFICATION_ID,
            builder
                .setSmallIcon(host.notificationIcon)
                .setContentTitle("Measure your waist")
                .setContentText(TEXT)
                .setStyle(Notification.BigTextStyle().bigText(TEXT))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build(),
        )
    }

    private fun alarm(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        NOTIFICATION_ID,
        Intent(context, WaistReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private val reminderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** The Sunday alarm: shows the reminder if the waist is still due, then sets next week's. */
class WaistReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val host = context.applicationContext as? GymHost ?: return
        val pending = goAsync()
        reminderScope.launch {
            try {
                if (host.gym.waistDue()) WaistReminder.show(context, host)
            } finally {
                WaistReminder.schedule(context)
                pending.finish()
            }
        }
    }
}

/** Alarms are cleared when the phone restarts or the app updates, so set it again then. */
class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            WaistReminder.schedule(context)
        }
    }
}