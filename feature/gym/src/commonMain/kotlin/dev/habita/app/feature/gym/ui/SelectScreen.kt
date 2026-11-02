package dev.habita.app.feature.gym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.habita.app.core.network.ApiException
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.ui.BackLink
import dev.habita.app.core.ui.Chip
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HIcon
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.HabitaIcons
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.SecondaryButton
import dev.habita.app.feature.gym.data.SyncState
import dev.habita.app.feature.gym.workout.ActiveWorkout
import dev.habita.app.shared.gym.Program
import dev.habita.app.shared.gym.Workout

/**
 * Picks a workout. Rotation workouts are grouped by warm-up into day cards (Upper day: A, B), and
 * fallback workouts get a dark card. The suggested next workout is the filled chip.
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
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        BackLink("Home", onBack)
        Gap(12.dp)
        HText("Gym", type.headline)
        val program = data.program
        if (program == null) {
            Gap(24.dp)
            NoProgram(data.sync, onRetry)
            return@Column
        }
        data.suggested?.let {
            Gap(6.dp)
            HText("Up next: ${it.name}", type.body, color = colors.muted)
        }
        Gap(24.dp)
        data.active?.let {
            InProgressCard(it, onResume, onDiscard)
            Gap(16.dp)
        }
        DayCards(program, data.suggested, onOpenWorkout)
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
    var confirmDiscard by remember(active.sessionId) { mutableStateOf(false) }
    HabitaCard(Modifier.fillMaxWidth(), color = colors.accentTint, borderColor = colors.accentTint) {
        HText("In progress", HabitaTheme.typography.caption, color = colors.accent)
        Gap(4.dp)
        HText(active.workout.name, HabitaTheme.typography.title)
        Gap(16.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        days.forEach { (warmUpId, workouts) ->
            val warmUp = program.warmUps.firstOrNull { it.id == warmUpId }
            HabitaCard(Modifier.fillMaxWidth()) {
                HText("${warmUp?.name ?: workouts.first().name} day", type.title)
                Gap(4.dp)
                HText(dayFocus(warmUpId) ?: "${workouts.first().exercises.size} exercises", type.body, color = colors.muted)
                Gap(16.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        Gap(4.dp)
                        HText("Fallback. Doesn't move the rotation.", type.body, color = colors.onInverseCard.copy(alpha = 0.7f))
                    }
                    HIcon(HabitaIcons.ChevronRight, contentDescription = null, tint = colors.onInverseCard)
                }
            }
        }
    }
}

/** What each day trains, for the warm-ups in the default program. */
private fun dayFocus(warmUpId: String): String? = when (warmUpId) {
    "upper" -> "Chest, back, shoulders, arms"
    "lower" -> "Legs, glutes, core"
    else -> null
}