package dev.habita.app.feature.gym.android

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import dev.habita.app.core.data.nowMillis
import dev.habita.app.feature.gym.ui.RingState
import kotlinx.coroutines.flow.StateFlow

/**
 * The alarm that rings when a rest ends, like an alarm clock: the chosen alarm sound on the alarm
 * stream, so a phone on silent still rings, looping with a buzz until it is dismissed or has rung
 * for a minute.
 *
 * With the phone locked or the app in the background, an exact system alarm starts it at the
 * rest's end. A foreground service keeps the app alive while it rings and shows a notification
 * with a Dismiss button. On screen, the rest timer starts it and the workout screen dismisses it.
 */
object RestEndAlarm {
    private const val CHANNEL = "rest-alarm"

    /** The channel before the alarm: it played a notification sound and can't be changed. */
    private const val OLD_CHANNEL = "rest-timer"
    private const val NOTIFICATION_ID = 11
    private const val DISMISS_REQUEST = 12
    internal const val ACTION_DISMISS = "dev.habita.app.feature.gym.REST_ALARM_DISMISS"
    private const val PREFERENCES = "rest-alarm"
    private const val TONE = "tone"

    /** Half a second on, half off: firm enough to feel through a pocket. */
    private val PATTERN = longArrayOf(0, 500, 500)

    private val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val state = RingState()
    private val main = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var focus: AudioFocusRequest? = null
    private var appContext: Context? = null
    private val expire = Runnable { appContext?.let { if (state.expire(nowMillis())) silence(it) } }

    /** Whether the alarm is ringing, wherever it was started. */
    val ringing: StateFlow<Boolean> get() = state.ringing

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

    /** Takes back the alarm if it hasn't rung yet. One already ringing goes on until [stop]. */
    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarm(context))
    }

    /** Starts ringing now; nothing happens when it already rings. Call on the main thread. */
    fun ring(context: Context) {
        val app = context.applicationContext
        if (!state.start(nowMillis())) return
        appContext = app
        play(app)
        main.removeCallbacks(expire)
        main.postDelayed(expire, state.remainingMillis(nowMillis()))
        try {
            app.startForegroundService(Intent(app, RestAlarmService::class.java))
        } catch (_: IllegalStateException) {
            // Android refused a foreground service from the background, as it may when the alarm
            // wasn't exact. The sound still plays; the notification still offers Dismiss.
            notificationManager(app).notify(NOTIFICATION_ID, notification(app))
        }
    }

    /** Stops the alarm ringing and clears its notification. Call on the main thread. */
    fun stop(context: Context) {
        if (state.stop()) silence(context.applicationContext)
    }

    /** The chosen alarm sound, or `null` for the phone's default alarm. */
    fun tone(context: Context): Uri? =
        preferences(context).getString(TONE, null)?.let(Uri::parse)

    /**
     * Keeps [uri] as the alarm sound on this phone only: a sound on one phone may not exist on
     * another. `null`, or the default alarm itself, follows the phone's default alarm.
     */
    fun setTone(context: Context, uri: Uri?) {
        val kept = uri?.takeUnless { it == Settings.System.DEFAULT_ALARM_ALERT_URI }
        preferences(context).edit().apply { if (kept == null) remove(TONE) else putString(TONE, kept.toString()) }.apply()
    }

    /** The alarm sound's name, as the system sound picker shows it. */
    fun toneName(context: Context): String {
        val uri = tone(context) ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        return runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull() ?: "Default alarm"
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private fun silence(context: Context) {
        main.removeCallbacks(expire)
        ringtone?.stop()
        ringtone = null
        vibrator(context).cancel()
        focus?.let { context.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(it) }
        focus = null
        RestAlarmService.running?.let {
            it.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            it.stopSelf()
        }
        notificationManager(context).cancel(NOTIFICATION_ID)
    }

    private fun play(context: Context) {
        // Music playing in the gym pauses while the alarm rings, and comes back after.
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(ATTRIBUTES)
            .build()
            .also { context.getSystemService(AudioManager::class.java)?.requestAudioFocus(it) }
        ringtone = sound(context)?.also {
            runCatching {
                it.audioAttributes = ATTRIBUTES
                it.isLooping = true
                it.play()
            }
        }
        @Suppress("DEPRECATION")
        vibrator(context).vibrate(VibrationEffect.createWaveform(PATTERN, 0), ATTRIBUTES)
    }

    /**
     * The chosen sound, falling back to the default alarm, then the ringtone when the phone has no
     * alarm sound. A [Ringtone] plays through the system when the app can't open the file itself.
     */
    private fun sound(context: Context): Ringtone? {
        val noDefaultAlarm = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM) == null
        val candidates = listOfNotNull(
            tone(context),
            Settings.System.DEFAULT_ALARM_ALERT_URI.takeUnless { noDefaultAlarm },
            Settings.System.DEFAULT_RINGTONE_URI,
        )
        return candidates.firstNotNullOfOrNull { runCatching { RingtoneManager.getRingtone(context, it) }.getOrNull() }
    }

    private fun vibrator(context: Context): Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private fun notificationManager(context: Context): NotificationManager {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(OLD_CHANNEL)
        // Silent itself: the app plays the chosen sound on the alarm stream and buzzes.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Rest alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rings when a rest between sets ends, with a button to dismiss it"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        return manager
    }

    /** "Rest is over", with Dismiss; tapping it opens the app, swiping it away dismisses too. */
    internal fun notification(context: Context): Notification {
        notificationManager(context)
        val dismiss = PendingIntent.getBroadcast(
            context,
            DISMISS_REQUEST,
            Intent(context, RestEndReceiver::class.java).setAction(ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(context, CHANNEL)
            .setContentTitle("Rest is over")
            .setContentText("Time for the next set.")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setDeleteIntent(dismiss)
            .addAction(Notification.Action.Builder(null, "Dismiss", dismiss).build())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        val host = context.applicationContext as? GymHost
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
        return builder.build()
    }

    internal fun notificationId(): Int = NOTIFICATION_ID

    private fun alarm(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        NOTIFICATION_ID,
        Intent(context, RestEndReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** The rest's end, set by [RestEndAlarm], and the notification's Dismiss button. */
class RestEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == RestEndAlarm.ACTION_DISMISS) RestEndAlarm.stop(context) else RestEndAlarm.ring(context)
    }
}

/**
 * Keeps the app running while the rest alarm rings with the phone locked, and shows its
 * notification. [RestEndAlarm] plays the sound; this only stays up until it stops.
 */
class RestAlarmService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires the notification first, even when the alarm was dismissed meanwhile.
        val notification = RestEndAlarm.notification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(RestEndAlarm.notificationId(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(RestEndAlarm.notificationId(), notification)
        }
        running = this
        if (!RestEndAlarm.ringing.value) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (running === this) running = null
        super.onDestroy()
    }

    internal companion object {
        /** The service while it is up, so a dismissal can take it down without a stopService race. */
        var running: RestAlarmService? = null
    }
}