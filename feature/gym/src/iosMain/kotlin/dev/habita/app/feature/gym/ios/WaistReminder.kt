package dev.habita.app.feature.gym.ios

import dev.habita.app.feature.gym.GymFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * The Sunday waist reminder on iOS. iOS can't run code at reminder time, so the next four
 * Sundays that need one are queued ahead, and queued again whenever the body log changes: logging
 * the waist on a Sunday removes that day's reminder. Local notifications only, no push service.
 */
object WaistReminder {
    private const val SLOTS = 4
    private const val HOUR = 9L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false

    /** Asks for permission once, then keeps the queue in line with the body log. */
    fun start(gym: GymFeature) {
        if (started) return
        started = true
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ -> }
        scope.launch {
            gym.waistReminderDays.collect { days -> queue(center, days) }
        }
    }

    private fun queue(center: UNUserNotificationCenter, days: List<String>) {
        center.removePendingNotificationRequestsWithIdentifiers((0 until SLOTS).map { "waist-$it" })
        days.forEachIndexed { index, day ->
            val (year, month, date) = day.split('-').map { it.toLong() }
            val at = NSDateComponents().apply {
                setYear(year)
                setMonth(month)
                setDay(date)
                setHour(HOUR)
            }
            val content = UNMutableNotificationContent().apply {
                setTitle("Measure your waist")
                setBody("At the belly button, relaxed, after breathing out. Then log it in the gym.")
                setSound(UNNotificationSound.defaultSound)
            }
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(at, repeats = false)
            center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier("waist-$index", content, trigger), null)
        }
    }
}