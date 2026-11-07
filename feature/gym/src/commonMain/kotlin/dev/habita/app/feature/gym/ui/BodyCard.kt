package dev.habita.app.feature.gym.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.Chip
import dev.habita.app.core.ui.Divider
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.LabeledField
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.SecondaryButton
import dev.habita.app.core.ui.TextAction
import dev.habita.app.core.ui.appear
import dev.habita.app.feature.gym.data.TrendPoint
import dev.habita.app.shared.gym.Body
import dev.habita.app.shared.gym.BodyAdvice
import dev.habita.app.shared.gym.BodyGoal
import dev.habita.app.shared.gym.BodyLog
import dev.habita.app.shared.gym.BodyTrend
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The morning weigh-in, the weekly average and what to change in the diet. Advice is relative to
 * what is eaten now, so the app never needs a calorie target.
 */
@Composable
internal fun BodyCard(
    trend: BodyTrend,
    logs: List<BodyLog>,
    today: String,
    onWeight: (Int) -> Unit,
    onWaist: (Int) -> Unit,
    onSetDay: (day: String, weightGrams: Int?, waistMm: Int?) -> Unit,
    onGoal: (BodyGoal) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val todayWeight = trend.today?.weightGrams
    var editingWeight by remember { mutableStateOf(false) }
    var editingWaist by remember { mutableStateOf(false) }
    var showPast by remember { mutableStateOf(false) }

    HabitaCard(modifier.fillMaxWidth().appear(2)) {
        Overline("Bodyweight")
        Gap(Space.inside)
        if (todayWeight == null || editingWeight) {
            NumberEntry(
                label = "This morning, kg",
                initial = todayWeight?.let(::formatKg).orEmpty(),
                parse = ::parseWeight,
                onSave = { onWeight(it); editingWeight = false },
            )
            Gap(Space.tiny)
            HText("After the toilet, before eating or drinking.", type.caption, color = colors.muted)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HText("${formatOneDecimal(todayWeight)} kg", type.title)
                Gap(Space.item)
                HText("today", type.caption, color = colors.muted)
                Spacer(Modifier.weight(1f))
                TextAction("Edit", { editingWeight = true }, color = colors.muted)
            }
        }

        val thisWeek = trend.weeks.firstOrNull { it.weeksAgo == 0 }
        if (thisWeek != null) {
            Gap(Space.card)
            if (trend.weeks.size >= 2) {
                Sparkline(
                    trend.weeks.reversed().map { TrendPoint(-it.weeksAgo.toLong(), it.grams / 1000.0, it.weeksAgo == 0) },
                )
                Gap(Space.inside)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HText("Week average ${formatOneDecimal(thisWeek.grams)} kg", type.bodyStrong, modifier = Modifier.weight(1f))
                trend.weeklyChangeGrams?.let { change ->
                    HText(
                        "${signed(change)} kg vs last week",
                        type.caption,
                        color = if (change < 0) colors.accent else colors.muted,
                    )
                }
            }
        }

        Gap(Space.card)
        Divider()
        Gap(Space.card)
        val (title, detail) = adviceText(trend)
        HText(title, type.bodyStrong)
        Gap(Space.tiny)
        HText(detail, type.caption, color = colors.muted)
        Gap(Space.item)
        HText("Goal", type.label)
        Gap(Space.inside)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.inside)) {
            BodyGoal.entries.forEach { goal ->
                Chip(goal.label(), selected = trend.goal == goal, onClick = { onGoal(goal) }, modifier = Modifier.weight(1f))
            }
        }

        Gap(Space.card)
        Divider()
        Gap(Space.card)
        val waist = trend.latestWaist
        val waistToday = trend.today?.waistMm
        if (editingWaist) {
            NumberEntry(
                label = "Waist, cm",
                initial = waistToday?.let { formatOneDecimal(it * 100) }.orEmpty(),
                parse = ::parseWaist,
                onSave = { onWaist(it); editingWaist = false },
            )
            Gap(Space.tiny)
            HText("Around the belly button, relaxed, after breathing out.", type.caption, color = colors.muted)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (waist != null) {
                        HText("Waist ${formatOneDecimal(waist.waistMm!! * 100)} cm", type.bodyStrong)
                        HText(
                            trend.waistChangeMm?.takeIf { it != 0 }?.let { "${signed(-it * 100)} cm in 4 weeks" }
                                ?: "Last measured ${daysAgo(waist.day)}",
                            type.caption,
                            color = colors.muted,
                        )
                    } else {
                        HText("Waist", type.bodyStrong)
                        HText("Once a week shows fat loss the scale can hide.", type.caption, color = colors.muted)
                    }
                }
                TextAction(if (waistToday != null) "Edit" else "Log waist", { editingWaist = true })
            }
        }

        val notifications = rememberNotificationAccess()
        if (notifications != null && !notifications.enabled) {
            Gap(Space.inside)
            Row(verticalAlignment = Alignment.CenterVertically) {
                HText("Sunday reminders are off.", type.caption, color = colors.muted, modifier = Modifier.weight(1f))
                TextAction("Turn on", notifications.request)
            }
        }
        Gap(Space.card)
        Divider()
        Gap(Space.inside)
        Row(verticalAlignment = Alignment.CenterVertically) {
            HText("Past two weeks", type.label, modifier = Modifier.weight(1f))
            TextAction(if (showPast) "Hide" else "Show", { showPast = !showPast }, color = colors.muted)
        }
        if (showPast) {
            Gap(Space.inside)
            HText("Tap a day to fix it, or to add one you missed.", type.caption, color = colors.muted)
            Gap(Space.inside)
            PastDays(logs, today, onSetDay)
        }
    }
}

/** The last 14 days, each one editable: a missed gym weigh-in can be added, a typo fixed. */
@Composable
private fun PastDays(logs: List<BodyLog>, today: String, onSetDay: (String, Int?, Int?) -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val byDay = logs.filter { !it.deleted }.associateBy { it.day }
    var editing by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.inside)) {
        Body.lastDays(today, PAST_DAYS).forEach { day ->
            val log = byDay[day]
            if (editing == day) {
                DayEditor(day, log, today, onSave = { weight, waist ->
                    onSetDay(day, weight, waist)
                    editing = null
                }, onCancel = { editing = null })
            } else {
                Row(
                    Modifier.fillMaxWidth().clickable { editing = day }.padding(vertical = Space.tiny),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HText(dayLabel(day, today), type.body, modifier = Modifier.weight(1f))
                    HText(
                        listOfNotNull(
                            log?.weightGrams?.let { "${formatOneDecimal(it)} kg" },
                            log?.waistMm?.let { "${formatOneDecimal(it * 100)} cm" },
                        ).joinToString(" · ").ifEmpty { "–" },
                        type.body,
                        color = if (log == null) colors.muted else colors.text,
                    )
                }
            }
        }
    }
}

/** Both values of one day. Clearing both and saving deletes the day. */
@Composable
private fun DayEditor(day: String, log: BodyLog?, today: String, onSave: (Int?, Int?) -> Unit, onCancel: () -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    // The weight as logged, not rounded, so saving only a waist leaves it exactly as it was.
    var weightText by remember(day) { mutableStateOf(log?.weightGrams?.let(::formatKg).orEmpty()) }
    var waistText by remember(day) { mutableStateOf(log?.waistMm?.let { formatOneDecimal(it * 100) }.orEmpty()) }
    val weight = parseWeight(weightText)
    val waist = parseWaist(waistText)
    // A blank field means no value; anything typed must be a real number to save.
    val valid = (weightText.isBlank() || weight != null) && (waistText.isBlank() || waist != null)
    Column(Modifier.fillMaxWidth()) {
        HText(dayLabel(day, today), type.bodyStrong)
        Gap(Space.inside)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.item)) {
            LabeledField(
                label = "Weight, kg",
                value = weightText,
                onValueChange = { weightText = numberInput(it) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            )
            LabeledField(
                label = "Waist, cm",
                value = waistText,
                onValueChange = { waistText = numberInput(it) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            )
        }
        Gap(Space.item)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.inside)) {
            SecondaryButton("Cancel", onCancel, Modifier.weight(1f))
            PrimaryButton(
                if (weightText.isBlank() && waistText.isBlank() && log != null) "Delete day" else "Save",
                { onSave(weight, waist) },
                Modifier.weight(1f),
                enabled = valid && (log != null || weight != null || waist != null),
            )
        }
        if (!valid) {
            Gap(Space.tiny)
            HText("Weight 20–400 kg, waist 40–200 cm.", type.caption, color = colors.error)
        }
    }
}

private const val PAST_DAYS = 14

private val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** "Today", "Yesterday" or "Thu 5 Nov". */
private fun dayLabel(day: String, today: String): String = when (Body.epochDay(today) - Body.epochDay(day)) {
    0L -> "Today"
    1L -> "Yesterday"
    // Noon UTC is the same calendar day in every time zone the app is likely used in.
    else -> "${weekdays[Body.dayOfWeek(day)]} ${shortDate(Body.epochDay(day) * 86_400_000 + 43_200_000)}"
}

private fun numberInput(text: String): String = text.filter { it.isDigit() || it == '.' || it == ',' }.take(6)

private fun parseWeight(text: String): Int? = parseKg(text)?.takeIf { it in 20_000..400_000 }

/** Centimetres as typed ("85.5") to millimetres. */
private fun parseWaist(text: String): Int? = parseKg(text)?.let { it / 100 }?.takeIf { it in 400..2000 }

/** A number field with a Save button. [parse] returns `null` for anything that can't be saved. */
@Composable
private fun NumberEntry(label: String, initial: String, parse: (String) -> Int?, onSave: (Int) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    val value = parse(text)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.item)) {
        LabeledField(
            label = label,
            value = text,
            onValueChange = { text = numberInput(it) },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { value?.let(onSave) }),
        )
        PrimaryButton("Save", { value?.let(onSave) }, enabled = value != null)
    }
}

private fun adviceText(trend: BodyTrend): Pair<String, String> {
    val change = trend.weeklyChangeGrams?.let { formatOneDecimal(abs(it)) }
    return when (trend.advice) {
        BodyAdvice.NEED_MORE_DATA -> "Weigh in most mornings" to
            "Daily weight jumps around with water and food. Advice starts once there are two weeks with " +
            "at least ${Body.MIN_WEIGH_INS} weigh-ins each."
        BodyAdvice.TOO_EARLY -> "Too early to change anything" to when (trend.goal) {
            BodyGoal.LOSE_FAT -> "No drop this week yet."
            BodyGoal.MAINTAIN -> "That's a big move for one week."
            BodyGoal.BUILD_MUSCLE -> "No gain this week yet."
        } + " One week can be water. Keep eating the same and check again next week."
        BodyAdvice.ON_TRACK -> "On track, keep eating the same" to when (trend.goal) {
            BodyGoal.LOSE_FAT -> if ((trend.weeklyChangeGrams ?: 0) <= 0) {
                "Down $change kg this week. That's a pace that keeps your muscle."
            } else {
                "Up $change kg this week, but down over two weeks. One week can be water."
            }
            BodyGoal.MAINTAIN -> "Your weight is holding steady."
            BodyGoal.BUILD_MUSCLE -> "A slow, steady gain: mostly muscle, little fat."
        }
        BodyAdvice.TOO_FAST -> "Losing fast: eat about 150 kcal a day more" to
            "Down $change kg in a week, over 1% of your weight. That fast, you start losing muscle too."
        BodyAdvice.STALLED -> "Stalled: eat about 150 kcal a day less" to
            "No real change in two weeks. Cut about 150 kcal a day, or walk 2–3k more steps. Change one " +
            "thing, then give it two weeks."
        BodyAdvice.RECOMP -> "Weight flat, waist down: keep going" to
            "Your waist is down ${formatOneDecimal((trend.waistChangeMm ?: 0) * 100)} cm in 4 weeks while the " +
            "scale stays put. That's fat going and muscle coming. Eat the same."
        BodyAdvice.DRIFTING_UP -> "Creeping up: eat about 100 kcal a day less" to
            "Over half a kilo up in two weeks. A small cut brings it back to steady."
        BodyAdvice.DRIFTING_DOWN -> "Dropping: eat about 100 kcal a day more" to
            "Over half a kilo down in two weeks. A little more food keeps it steady."
        BodyAdvice.NOT_GAINING -> "Not gaining: eat about 150 kcal a day more" to
            "Under 0.2 kg gained in two weeks. Muscle needs a small surplus. Change it, then give it two weeks."
        BodyAdvice.GAINING_FAST -> "Gaining fast: eat about 150 kcal a day less" to
            "Up $change kg in a week, over 0.5% of your weight. Most of that much is fat, not muscle."
    }
}

/** Thousandths to one decimal: 78_350 grams → "78.4". A waist in mm × 100 reads as cm the same way. */
private fun formatOneDecimal(grams: Int): String {
    val tenths = (grams / 100.0).roundToInt()
    return "${tenths / 10}.${abs(tenths % 10)}"
}

/** −400 grams → "−0.4", 300 → "+0.3". */
private fun signed(grams: Int): String = when {
    grams < 0 -> "−${formatOneDecimal(-grams)}"
    grams > 0 -> "+${formatOneDecimal(grams)}"
    else -> "±0.0"
}

private fun daysAgo(day: String): String = when (val days = (Body.epochDay(today()) - Body.epochDay(day)).toInt()) {
    0 -> "today"
    1 -> "yesterday"
    else -> "$days days ago"
}

private fun BodyGoal.label(): String = when (this) {
    BodyGoal.LOSE_FAT -> "Lose fat"
    BodyGoal.MAINTAIN -> "Maintain"
    BodyGoal.BUILD_MUSCLE -> "Build"
}