package dev.habita.app.feature.gym.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.habita.app.core.data.nowMillis
import dev.habita.app.core.network.ApiException
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.appear
import dev.habita.app.core.ui.Chip
import dev.habita.app.core.ui.Divider
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HIcon
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.HabitaIcons
import dev.habita.app.core.ui.LocalWideLayout
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.SecondaryButton
import dev.habita.app.core.ui.TopBar
import dev.habita.app.core.ui.TwoPane
import dev.habita.app.core.sync.SyncState
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
    onBackup: () -> Unit,
    backupMessage: String?,
    onWeight: (Int) -> Unit,
    onWaist: (Int) -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val wide = LocalWideLayout.current
    val program = data.program
    if (program == null) {
        Column(Modifier.fillMaxSize().padding(horizontal = Space.screen, vertical = Space.inside)) {
            TopBar("Home", onBack)
            Gap(Space.inside)
            HText("Gym", type.headline)
            Gap(Space.section)
            NoProgram(data.sync, onRetry)
        }
        return
    }
    val now = nowMillis()
    val weekStart = startOfWeek(now)
    val thisWeek = data.sessions.filter { !it.deleted && it.endedAt != null && it.startedAt >= weekStart }
    TwoPane(
        first = {
            TopBar("Home", onBack)
            Gap(Space.inside)
            HText("Gym", type.headline)
            Gap(Space.tiny)
            HText(weekLine(thisWeek.size), type.body, color = colors.muted)
            Gap(Space.card)
            WeekStrip(thisWeek, weekStart, now)
            Gap(Space.section)
            data.active?.let { InProgressCard(it, onResume, onDiscard) }
            data.suggested?.let { workout ->
                if (data.active == null) {
                    UpNextCard(program, workout, lastDone(data.sessions, workout.id, now)) { onOpenWorkout(workout.id) }
                }
            }
            Gap(Space.item)
            BodyCard(data.body, onWeight, onWaist)
            if (wide) {
                Gap(Space.section)
                StorageNote(data, onBackup, backupMessage)
            }
        },
        second = {
            // Lines the list up with the title in the first pane.
            if (wide) Gap(56.dp)
            Overline("All workouts")
            Gap(Space.item)
            DayCards(program, onOpenWorkout)
            if (!wide) {
                Gap(Space.section)
                StorageNote(data, onBackup, backupMessage)
            }
            Gap(Space.card)
        },
    )
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

/**
 * Monday to Sunday: the day letter over a short bar, filled for each day with a finished workout.
 * Today's letter and bar are in the accent until a workout fills it.
 */
@Composable
private fun WeekStrip(sessions: List<WorkoutSession>, weekStart: Long, now: Long) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val today = daysBetween(weekStart, now)
    val doneDays = sessions.map { daysBetween(weekStart, it.startedAt) }.toSet()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, letter ->
            val done = index in doneDays
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) {
                    if (done) contentDescription = "Worked out"
                },
            ) {
                HText(letter, type.caption, color = if (index == today) colors.accent else colors.muted)
                Gap(6.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                done -> colors.highlight
                                index == today -> colors.accent
                                else -> colors.border
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun UpNextCard(program: Program, workout: Workout, lastDone: String?, onOpen: () -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val warmUp = program.warmUps.firstOrNull { it.id == workout.warmUpId }
    HabitaCard(Modifier.fillMaxWidth().appear(1), onClick = onOpen) {
        Overline("Up next", color = colors.accent)
        Gap(Space.tiny)
        HText(workout.name, type.headline)
        Gap(Space.tiny)
        HText(
            "${workout.exercises.size} exercises · ${workout.exercises.sumOf { it.sets }} sets · ~${estimateMinutes(workout, warmUp)} min",
            type.body,
            color = colors.muted,
        )
        if (lastDone != null) HText(lastDone, type.caption, color = colors.muted)
        Gap(Space.card)
        PrimaryButton("See workout", onOpen, Modifier.fillMaxWidth())
    }
}

@Composable
private fun NoProgram(sync: SyncState, onRetry: () -> Unit) {
    val colors = HabitaTheme.colors
    HabitaCard(Modifier.fillMaxWidth().appear(2)) {
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
    HabitaCard(Modifier.fillMaxWidth().appear(3), borderColor = colors.accent) {
        Overline("In progress", color = colors.accent)
        Gap(Space.inside)
        HText(active.workout.name, type.title)
        Gap(Space.tiny)
        HText(
            "${active.setsLogged.sum()} of ${active.workout.exercises.sumOf { it.sets }} sets done",
            type.caption,
            color = colors.muted,
        )
        Gap(Space.card)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.inside)) {
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
private fun DayCards(program: Program, onOpenWorkout: (String) -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val days = program.workouts
        .filter { it.rotation != null }
        .sortedBy { it.rotation }
        .groupBy { it.warmUpId }
    Column(verticalArrangement = Arrangement.spacedBy(Space.item)) {
        days.forEach { (warmUpId, workouts) ->
            val warmUp = program.warmUps.firstOrNull { it.id == warmUpId }
            HabitaCard(Modifier.fillMaxWidth().appear(4)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        HText("${warmUp?.name ?: workouts.first().name} day", type.title)
                        HText(dayFocus(warmUpId) ?: "${workouts.first().exercises.size} exercises", type.caption, color = colors.muted)
                    }
                }
                Gap(Space.card)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.item)) {
                    workouts.forEach { workout ->
                        Chip(
                            text = workout.name.substringAfterLast(' '),
                            // Up next is shown above; here every workout is just a choice.
                            selected = false,
                            onClick = { onOpenWorkout(workout.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        program.workouts.filter { it.rotation == null }.forEach { workout ->
            HabitaCard(
                modifier = Modifier.fillMaxWidth().appear(5),
                onClick = { onOpenWorkout(workout.id) },
                color = colors.inverseCard,
                borderColor = colors.inverseCard,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        HText(workout.name, type.title, color = colors.onInverseCard)
                        Gap(Space.tiny)
                        HText(
                            "Short on time? Hits everything once. Doesn't move the rotation.",
                            type.caption,
                            color = colors.onInverseCard.copy(alpha = 0.7f),
                        )
                    }
                    Gap(Space.item)
                    HIcon(HabitaIcons.ChevronRight, contentDescription = null, tint = colors.onInverseCard)
                }
            }
        }
    }
}

/**
 * Where the workouts are kept: on the phone only, or synced with the server, and a way to save a
 * backup file of everything on the phone.
 */
@Composable
private fun ColumnScope.StorageNote(data: GymData, onBackup: () -> Unit, backupMessage: String?) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val (title, detail) = when {
        !data.syncEnabled -> "Saved on this phone" to "Everything uploads once the server is set up."
        data.sync is SyncState.Failed -> "Saved on this phone" to "Will sync when the server is reachable."
        data.sync is SyncState.Syncing -> "Syncing…" to "Saved on this phone too."
        else -> "Synced" to "Saved on this phone and the server."
    }
    Divider()
    Gap(Space.card)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HIcon(
                if (data.syncEnabled && data.sync !is SyncState.Failed) HabitaIcons.Cloud else HabitaIcons.Phone,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colors.muted,
            )
            Gap(Space.item)
            Column(Modifier.weight(1f)) {
                HText(title, type.label)
                HText(detail, type.caption, color = colors.muted)
            }
        }
        Gap(Space.item)
        SecondaryButton("Save a backup file", onBackup, Modifier.fillMaxWidth())
        if (backupMessage != null) {
            Gap(Space.inside)
            HText(backupMessage, type.caption, color = colors.muted)
        }
    }
}

/** What each day trains, for the warm-ups in the default program. */
private fun dayFocus(warmUpId: String): String? = when (warmUpId) {
    "upper" -> "Chest, back, shoulders, arms"
    "lower" -> "Legs, glutes, core"
    else -> null
}