package dev.habita.app.feature.gym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.shared.gym.Program
import dev.habita.app.shared.gym.WorkoutSummary
import kotlin.math.abs
import kotlin.math.roundToInt

/** The six end-of-workout numbers. */
@Composable
internal fun SummaryScreen(
    summary: WorkoutSummary?,
    program: Program?,
    workoutName: String?,
    onDone: () -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    fun names(ids: List<String>) =
        if (ids.isEmpty()) "None this time" else ids.joinToString("\n") { program?.exercise(it)?.name ?: it }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Gap(32.dp)
            HText("Workout done", type.headline)
            workoutName?.let {
                Gap(6.dp)
                HText(it, type.body, color = colors.muted)
            }
            Gap(24.dp)
            if (summary == null) {
                HText("Adding it up…", type.body, color = colors.muted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard(
                        "Time",
                        formatDuration(summary.totalMillis),
                        "Warm-up ${formatDuration(summary.warmUpMillis)} · main ${formatDuration(summary.mainMillis)}",
                    )
                    SummaryCard("Volume", "${formatVolume(summary.volumeKg)} kg", volumeChange(summary, workoutName))
                    SummaryCard("Progressed", names(summary.exercisesProgressed))
                    SummaryCard("New bests", names(summary.personalBests))
                    SummaryCard("Sets", "${summary.setsCompleted} done · ${summary.setsSkipped} skipped")
                    SummaryCard("Heavier next time", names(summary.nextIncreases))
                }
            }
        }
        PrimaryButton("Done", onDone, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp))
    }
}

private fun volumeChange(summary: WorkoutSummary, workoutName: String?): String {
    val change = summary.volumeChangePercent ?: return "First ${workoutName ?: "time"} to compare with"
    val rounded = change.roundToInt()
    return when {
        rounded > 0 -> "Up $rounded% on last time"
        rounded < 0 -> "Down ${abs(rounded)}% on last time"
        else -> "Same as last time"
    }
}

@Composable
private fun SummaryCard(label: String, value: String, detail: String? = null) {
    val colors = HabitaTheme.colors
    HabitaCard(Modifier.fillMaxWidth()) {
        HText(label, HabitaTheme.typography.caption, color = colors.muted)
        Gap(4.dp)
        HText(value, HabitaTheme.typography.title)
        if (detail != null) {
            Gap(4.dp)
            HText(detail, HabitaTheme.typography.body, color = colors.muted)
        }
    }
}