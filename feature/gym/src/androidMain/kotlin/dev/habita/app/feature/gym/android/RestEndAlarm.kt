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
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Rings when a rest ends while the app is in the background, such as with the phone locked in the
 * gym: an exact system alarm that shows a notification with sound and a buzz. The sound plays as
 * an alarm, so a phone on silent still rings at alarm volume. Without notification access it only
 * buzzes.
 */
object RestEndAlarm {
    private const val CHANNEL = "rest-timer"
    private const val NOTIFICATION_ID = 11
    private val PATTERN = longArrayOf(0, 400, 200, 400, 200, 400)

    /** Sets the alarm for [endsAt]; replaces the one before. */
    fun set(context: Context, endsAt: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        // Without exact alarms Android may run it a little late, which still beats not at all.
        if (exact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, alarm(context))
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, alarm(context))
        }
    }

    /** Takes back the alarm and clears its notification. */
    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarm(context))
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    internal fun ring(context: Context) {
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!canNotify || !manager.areNotificationsEnabled()) {
            buzz(context)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Rings when a rest between sets ends while the phone is locked"
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                    )
                    enableVibration(true)
                    vibrationPattern = PATTERN
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }
        val host = context.applicationContext as? GymHost
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL)
        } else {
            Notification.Builder(context)
                .setPriority(Notification.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
        }
        if (host != null) {
            builder.setSmallIcon(host.notificationIcon).setContentIntent(
                PendingIntent.getActivity(
                    context,
                    NOTIFICATION_ID,
                    host.launchIntent(context),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        }
        manager.notify(
            NOTIFICATION_ID,
            builder
                .setContentTitle("Rest is over")
                .setContentText("Time for the next set.")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun buzz(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(VibrationEffect.createWaveform(PATTERN, -1), attributes)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(PATTERN, -1, attributes)
        }
    }

    private fun alarm(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        NOTIFICATION_ID,
        Intent(context, RestEndReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** The rest's end, set by [RestEndAlarm]. */
class RestEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = RestEndAlarm.ring(context)
}