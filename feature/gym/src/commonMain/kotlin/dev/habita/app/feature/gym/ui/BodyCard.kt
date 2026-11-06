package dev.habita.app.feature.gym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.habita.app.core.ui.Divider
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.LabeledField
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.TextAction
import dev.habita.app.core.ui.appear
import dev.habita.app.feature.gym.data.TrendPoint
import dev.habita.app.shared.gym.Body
import dev.habita.app.shared.gym.BodyAdvice
import dev.habita.app.shared.gym.BodyTrend
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The morning weigh-in, the weekly average and what to change in the diet. Advice is relative to
 * what is eaten now, so the app never needs a calorie target.
 */
@Composable
internal fun BodyCard(trend: BodyTrend, onWeight: (Int) -> Unit, onWaist: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val todayWeight = trend.today?.weightGrams
    var editingWeight by remember { mutableStateOf(false) }
    var editingWaist by remember { mutableStateOf(false) }

    HabitaCard(modifier.fillMaxWidth().appear(2)) {
        Overline("Bodyweight")
        Gap(Space.inside)
        if (todayWeight == null || editingWeight) {
            NumberEntry(
                label = "This morning, kg",
                initial = todayWeight?.let(::formatKg).orEmpty(),
                parse = { parseKg(it)?.takeIf { grams -> grams in 20_000..400_000 } },
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

        Gap(Space.card)
        Divider()
        Gap(Space.card)
        val waist = trend.latestWaist
        val waistToday = trend.today?.waistMm
        if (editingWaist) {
            NumberEntry(
                label = "Waist, cm",
                initial = waistToday?.let { formatOneDecimal(it * 100) }.orEmpty(),
                parse = { parseKg(it)?.let { tenths -> tenths / 100 }?.takeIf { mm -> mm in 400..2000 } },
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
    }
}

/** A number field with a Save button. [parse] returns `null` for anything that can't be saved. */
@Composable
private fun NumberEntry(label: String, initial: String, parse: (String) -> Int?, onSave: (Int) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    val value = parse(text)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.item)) {
        LabeledField(
            label = label,
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) },
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
        BodyAdvice.TOO_EARLY -> "Too early to change anything" to
            "No drop this week yet. One week can be water. Keep eating the same and check again next week."
        BodyAdvice.ON_TRACK -> "On track, keep eating the same" to
            "Down $change kg this week. That's a pace that keeps your muscle."
        BodyAdvice.TOO_FAST -> "Losing fast: eat about 150 kcal a day more" to
            "Down $change kg in a week, over 1% of your weight. That fast, you start losing muscle too."
        BodyAdvice.STALLED -> "Stalled: eat about 150 kcal a day less" to
            "No real change in two weeks. Cut about 150 kcal a day, or walk 2–3k more steps. Change one " +
            "thing, then give it two weeks."
        BodyAdvice.RECOMP -> "Weight flat, waist down: keep going" to
            "Your waist is down ${formatOneDecimal((trend.waistChangeMm ?: 0) * 100)} cm in 4 weeks while the " +
            "scale stays put. That's fat going and muscle coming. Eat the same."
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