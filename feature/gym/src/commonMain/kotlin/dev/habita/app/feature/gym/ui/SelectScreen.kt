package dev.habita.app.feature.gym.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.habita.app.core.data.nowMillis
import dev.habita.app.core.network.ApiException
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.ui.Chip
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HIcon
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.HabitaIcons
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.SecondaryButton
import dev.habita.app.core.ui.TopBar
import dev.habita.app.feature.gym.data.SyncState
import dev.habita.app.feature.gym.workout.ActiveWorkout
import dev.habita.app.shared.gym.Program
import dev.habita.app.shared.gym.Workout
import dev.habita.app.shared.gym.WorkoutSession

/**
 * Picks a workout. The suggested next one gets the big card at the top; below it, rotation
 * workouts are grouped by warm-up into day cards (Upper day: A, B), and fallback workouts get a
 * dark card.
 */
@Composable
internal fun SelectScreen(
    data: GymData,
    onBack: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        TopBar("Home", onBack)
        Gap(8.dp)
        HText("Gym", type.headline)
        val program = data.program
        if (program == null) {
            Gap(24.dp)
            NoProgram(data.sync, onRetry)
            return@Column
        }
        val now = nowMillis()
        val weekStart = startOfWeek(now)
        val thisWeek = data.sessions.filter { !it.deleted && it.endedAt != null && it.startedAt >= weekStart }
        Gap(4.dp)
        HText(weekLine(thisWeek.size), type.body, color = colors.muted)
        Gap(16.dp)
        WeekStrip(thisWeek, weekStart, now)
        Gap(20.dp)
        data.active?.let {
            InProgressCard(it, onResume, onDiscard)
            Gap(16.dp)
        }
        data.suggested?.let { workout ->
            if (data.active == null) {
                UpNextCard(program, workout, lastDone(data.sessions, workout.id, now)) { onOpenWorkout(workout.id) }
                Gap(24.dp)
            }
        }
        Overline("All workouts")
        Gap(10.dp)
        DayCards(program, data.suggested, onOpenWorkout)
        Gap(20.dp)
        StorageNote(data)
        Gap(8.dp)
    }
}

private fun weekLine(count: Int): String = when (count) {
    0 -> "No workouts yet this week"
    1 -> "1 workout this week"
    else -> "$count workouts this week"
}

private fun lastDone(sessions: List<WorkoutSession>, workoutId: String, now: Long): String? {
    val last = sessions
        .filter { !it.deleted && it.endedAt != null && it.workoutId == workoutId }
        .maxByOrNull { it.startedAt } ?: return null
    return when (val days = daysBetween(last.startedAt, now)) {
        0 -> "Last done today"
        1 -> "Last done yesterday"
        else -> "Last done $days days ago"
    }
}

/** Monday to Sunday: a filled dot for each day with a finished workout, a ring around today. */
@Composable
private fun WeekStrip(sessions: List<WorkoutSession>, weekStart: Long, now: Long) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val today = daysBetween(weekStart, now)
    val doneDays = sessions.map { daysBetween(weekStart, it.startedAt) }.toSet()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, letter ->
            val done = index in doneDays
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HText(letter, type.caption, color = if (index == today) colors.text else colors.muted)
                Gap(6.dp)
                Box(
                    Modifier
                        .size(36.dp)
                        .background(if (done) colors.accent else colors.card, CircleShape)
                        .border(
                            width = if (index == today) 2.dp else 1.dp,
                            color = when {
                                done -> colors.accent
                                index == today -> colors.text
                                else -> colors.border
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) HIcon(HabitaIcons.Check, contentDescription = "Worked out", modifier = Modifier.size(18.dp), tint = colors.onAccent)
                }
            }
        }
    }
}

@Composable
private fun UpNextCard(program: Program, workout: Workout, lastDone: String?, onOpen: () -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val warmUp = program.warmUps.firstOrNull { it.id == workout.warmUpId }
    HabitaCard(
        Modifier.fillMaxWidth(),
        onClick = onOpen,
        color = colors.accent,
        borderColor = colors.accent,
    ) {
        Overline("Up next", color = colors.onAccent.copy(alpha = 0.75f))
        Gap(6.dp)
        HText(workout.name, type.headline, color = colors.onAccent)
        Gap(4.dp)
        HText(
            "${workout.exercises.size} exercises · ${workout.exercises.sumOf { it.sets }} sets · ~${estimateMinutes(workout, warmUp)} min",
            type.body,
            color = colors.onAccent.copy(alpha = 0.85f),
        )
        if (lastDone != null) {
            HText(lastDone, type.caption, color = colors.onAccent.copy(alpha = 0.7f))
        }
        Gap(16.dp)
        Row(
            Modifier.background(colors.onAccent, CircleShape).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HIcon(HabitaIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.accent)
            Gap(6.dp)
            HText("See workout", type.label, color = colors.accent)
        }
    }
}

@Composable
private fun NoProgram(sync: SyncState, onRetry: () -> Unit) {
    val colors = HabitaTheme.colors
    HabitaCard(Modifier.fillMaxWidth()) {
        if (sync is SyncState.Failed) {
            val message = when (sync.error) {
                is ApiException.Offline -> "Can't reach the server to load the workout plan. Connect once and it stays on the phone."
                else -> "Couldn't load the workout plan: ${sync.error.message}"
            }
            HText(message, HabitaTheme.typography.body, color = colors.muted)
            Gap(16.dp)
            SecondaryButton("Try again", onRetry, Modifier.fillMaxWidth())
        } else {
            HText("Loading the workout plan…", HabitaTheme.typography.body, color = colors.muted)
        }
    }
}

@Composable
private fun InProgressCard(active: ActiveWorkout, onResume: () -> Unit, onDiscard: () -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    var confirmDiscard by remember(active.sessionId) { mutableStateOf(false) }
    HabitaCard(Modifier.fillMaxWidth(), color = colors.accentTint, borderColor = colors.accentTint) {
        Overline("In progress", color = colors.accent)
        Gap(6.dp)
        HText(active.workout.name, type.title)
        Gap(2.dp)
        HText(
            "${active.setsLogged.sum()} of ${active.workout.exercises.sumOf { it.sets }} sets done",
            type.caption,
            color = colors.muted,
        )
        Gap(16.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Resume", onResume, Modifier.weight(1f))
            SecondaryButton(
                text = if (confirmDiscard) "Tap to confirm" else "Discard",
                onClick = { if (confirmDiscard) onDiscard() else confirmDiscard = true },
                modifier = Modifier.weight(1f),
                color = if (confirmDiscard) colors.error else colors.text,
            )
        }
    }
}

@Composable
private fun DayCards(program: Program, suggested: Workout?, onOpenWorkout: (String) -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val days = program.workouts
        .filter { it.rotation != null }
        .sortedBy { it.rotation }
        .groupBy { it.warmUpId }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        days.forEach { (warmUpId, workouts) ->
            val warmUp = program.warmUps.firstOrNull { it.id == warmUpId }
            HabitaCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        HText("${warmUp?.name ?: workouts.first().name} day", type.title)
                        HText(dayFocus(warmUpId) ?: "${workouts.first().exercises.size} exercises", type.caption, color = colors.muted)
                    }
                }
                Gap(14.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    workouts.forEach { workout ->
                        Chip(
                            text = workout.name.substringAfterLast(' '),
                            selected = workout.id == suggested?.id,
                            onClick = { onOpenWorkout(workout.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        program.workouts.filter { it.rotation == null }.forEach { workout ->
            HabitaCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenWorkout(workout.id) },
                color = colors.inverseCard,
                borderColor = colors.inverseCard,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        HText(workout.name, type.title, color = colors.onInverseCard)
                        Gap(2.dp)
                        HText(
                            "Short on time? Hits everything once. Doesn't move the rotation.",
                            type.caption,
                            color = colors.onInverseCard.copy(alpha = 0.7f),
                        )
                    }
                    Gap(12.dp)
                    HIcon(HabitaIcons.ChevronRight, contentDescription = null, tint = colors.onInverseCard)
                }
            }
        }
    }
}

/** Where the workouts are kept: on the phone only, or synced with the server. */
@Composable
private fun StorageNote(data: GymData) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val (title, detail) = when {
        !data.syncEnabled -> "Saved on this phone" to "Everything uploads once the server is set up."
        data.sync is SyncState.Failed -> "Saved on this phone" to "Will sync when the server is reachable."
        data.sync is SyncState.Syncing -> "Syncing…" to "Saved on this phone too."
        else -> "Synced" to "Saved on this phone and the server."
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        HIcon(
            if (data.syncEnabled && data.sync !is SyncState.Failed) HabitaIcons.Cloud else HabitaIcons.Phone,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colors.muted,
        )
        Gap(10.dp)
        Column {
            HText(title, type.label, color = colors.muted)
            HText(detail, type.caption, color = colors.muted)
        }
    }
}

/** What each day trains, for the warm-ups in the default program. */
private fun dayFocus(warmUpId: String): String? = when (warmUpId) {
    "upper" -> "Chest, back, shoulders, arms"
    "lower" -> "Legs, glutes, core"
    else -> null
}