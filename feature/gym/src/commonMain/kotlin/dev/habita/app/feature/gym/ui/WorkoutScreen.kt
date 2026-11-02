package dev.habita.app.feature.gym.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.habita.app.core.data.nowMillis
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.ui.BackLink
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.InfoRow
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.ProgressBar
import dev.habita.app.core.ui.SecondaryButton
import dev.habita.app.core.ui.Stepper
import dev.habita.app.core.ui.TextAction
import dev.habita.app.feature.gym.workout.ActiveWorkout
import dev.habita.app.feature.gym.workout.Phase
import dev.habita.app.feature.gym.workout.Rest
import dev.habita.app.shared.gym.Measure
import kotlinx.coroutines.delay

/** The workout in progress: warm-up, break, then the main workout set by set. */
@Composable
internal fun WorkoutScreen(
    active: ActiveWorkout,
    data: GymData,
    viewModel: GymViewModel,
    onBack: () -> Unit,
) {
    KeepScreenOn()
    var confirmFinish by remember(active.sessionId) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackLink("Gym", onBack)
            Box(Modifier.weight(1f))
            TextAction(
                text = if (confirmFinish) "Tap to finish" else "Finish",
                onClick = { if (confirmFinish) viewModel.finish() else confirmFinish = true },
                color = if (confirmFinish) HabitaTheme.colors.error else HabitaTheme.colors.accent,
            )
        }
        val rest = active.rest
        when {
            active.phase == Phase.WARM_UP -> WarmUpContent(active, viewModel)
            active.phase == Phase.BREAK && rest != null -> RestContent(
                title = "Break",
                rest = rest,
                next = active.currentSlot?.let { "Main workout starts with ${active.exercises[it].name}" },
                onDone = viewModel::endBreak,
            )
            rest != null -> RestContent(title = "Rest", rest = rest, next = nextSetText(active), onDone = viewModel::endRest)
            active.currentSlot != null -> ExerciseContent(active, data, viewModel)
        }
    }
}

@Composable
private fun WarmUpContent(active: ActiveWorkout, viewModel: GymViewModel) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val position = active.currentWarmUp ?: return
    val item = active.warmUp.items[position]
    val total = active.warmUp.items.size
    val next = active.warmUpQueue.getOrNull(1)?.let { active.warmUp.items[it].name }
    ActionLayout(
        actions = {
            PrimaryButton("Done", viewModel::warmUpDone, Modifier.fillMaxWidth())
            Gap(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Skip", viewModel::warmUpSkip, Modifier.weight(1f))
                SecondaryButton(
                    "Machine taken",
                    viewModel::warmUpMachineTaken,
                    Modifier.weight(1f),
                    enabled = active.warmUpQueue.size > 1,
                )
            }
            Gap(12.dp)
            HText(
                next?.let { "Next: $it" } ?: "Then a ${active.warmUp.restSeconds / 60} min break",
                type.caption,
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        HText("Warm-up · ${total - active.warmUpQueue.size + 1} of $total", type.label, color = colors.accent)
        Gap(8.dp)
        HText(item.name, type.headline)
        Gap(20.dp)
        HabitaCard(Modifier.fillMaxWidth()) {
            HText(item.amount, type.number)
        }
    }
}

@Composable
private fun RestContent(title: String, rest: Rest, next: String?, onDone: () -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    var now by remember { mutableLongStateOf(nowMillis()) }
    // Ticks until the rest is over, then moves on. This screen leaves as soon as the rest ends, so
    // a repeat call only happens if the first one was ignored while another action was running.
    LaunchedEffect(rest) {
        while (true) {
            now = nowMillis()
            if (rest.remainingMillis(now) == 0L) {
                onDone()
                delay(1000)
            } else {
                delay(200)
            }
        }
    }
    val remaining = rest.remainingMillis(now)
    ActionLayout(
        actions = {
            SecondaryButton(if (title == "Break") "Skip break" else "Skip rest", onDone, Modifier.fillMaxWidth())
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            HText(title, type.label, color = colors.accent)
            Gap(12.dp)
            HText(formatTimer(remaining), type.timer)
            Gap(24.dp)
            ProgressBar(1f - remaining.toFloat() / rest.totalMillis.coerceAtLeast(1))
            if (next != null) {
                Gap(24.dp)
                HText(next, type.body, color = colors.muted, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun nextSetText(active: ActiveWorkout): String? {
    val slotIndex = active.currentSlot ?: return null
    val slot = active.slot(slotIndex)
    val exercise = active.exercises[slotIndex]
    // Shows the weight used earlier today, which is what the next set is prefilled with.
    val target = active.targets[slotIndex].let { target ->
        active.lastWeights[slotIndex]?.let { target.copy(weightGrams = it) } ?: target
    }
    return "Next: ${exercise.name}, set ${active.setNumber(slotIndex)} of ${slot.sets} · ${target.describe(exercise)}"
}

@Composable
private fun ExerciseContent(active: ActiveWorkout, data: GymData, viewModel: GymViewModel) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val slotIndex = active.currentSlot ?: return
    val slot = active.slot(slotIndex)
    val exercise = active.exercises[slotIndex]
    val target = active.targets[slotIndex]
    val setNumber = active.setNumber(slotIndex)
    val step = data.step(exercise.id) ?: exercise.defaultStep

    // Prefilled with today's target, or with the weight just used, so a normal set is one tap.
    var weightText by remember(active.sessionId, slotIndex, setNumber) {
        mutableStateOf((active.lastWeights[slotIndex] ?: target.weightGrams)?.let(::formatKg) ?: "")
    }
    var amountText by remember(active.sessionId, slotIndex, setNumber) { mutableStateOf(target.target.toString()) }
    var editingStep by remember(slotIndex) { mutableStateOf(false) }

    val weightGrams = if (exercise.isWeighted) parseKg(weightText) else 0
    val amount = parseCount(amountText)
    val amountStep = if (exercise.measure == Measure.SECONDS) 5 else 1
    val nextName = active.nextSlot?.let { active.exercises[it].name }

    ActionLayout(
        actions = {
            PrimaryButton(
                "Done",
                onClick = { viewModel.logSet(weightGrams!!, amount!!) },
                enabled = weightGrams != null && amount != null,
                modifier = Modifier.fillMaxWidth(),
            )
            Gap(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Skip set", { viewModel.logSet(weightGrams ?: 0, null) }, Modifier.weight(1f))
                SecondaryButton(
                    "Machine taken",
                    viewModel::exerciseMachineTaken,
                    Modifier.weight(1f),
                    enabled = active.exerciseQueue.size > 1,
                )
            }
            Gap(12.dp)
            HText(
                nextName?.let { "Next: $it" } ?: "Last exercise",
                type.caption,
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        HText(
            "Exercise ${active.exercisesDone + 1} of ${active.workout.exercises.size} · Set $setNumber of ${slot.sets}",
            type.label,
            color = colors.accent,
        )
        Gap(8.dp)
        HText(exercise.name, type.headline)
        Gap(12.dp)
        SetProgress(done = active.setsLogged[slotIndex], total = slot.sets)
        Gap(20.dp)
        HabitaCard(Modifier.fillMaxWidth()) {
            InfoRow("Last time", target.lastTime?.describe(exercise) ?: "First time")
            Gap(10.dp)
            InfoRow("Today", target.describe(exercise))
            Gap(12.dp)
            HText(target.reasonText(exercise, step), type.caption, color = colors.muted)
        }
        Gap(24.dp)
        if (exercise.isWeighted) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HText(exercise.weightLabel(), type.label, modifier = Modifier.weight(1f))
                TextAction("Step ${exercise.formatStep(step)}", { editingStep = !editingStep })
            }
            Gap(8.dp)
            Stepper(
                value = weightText,
                onValueChange = { weightText = it },
                onMinus = { weightText = formatKg(((weightGrams ?: 0) - step).coerceAtLeast(0)) },
                onPlus = { weightText = formatKg((weightGrams ?: 0) + step) },
                unit = "kg",
                placeholder = "0",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                contentDescription = "weight",
            )
            if (editingStep) {
                Gap(12.dp)
                StepEditor(exercise.formatStep(step)) { change ->
                    val next = step + change * exercise.stepIncrement()
                    if (next > 0) viewModel.setStep(exercise.id, next)
                }
            }
            Gap(20.dp)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HText(exercise.amountLabel(), type.label, modifier = Modifier.weight(1f))
            if (!exercise.isWeighted) TextAction("Step ${exercise.formatStep(step)}", { editingStep = !editingStep })
        }
        Gap(8.dp)
        Stepper(
            value = amountText,
            onValueChange = { amountText = it },
            onMinus = { amountText = ((amount ?: 0) - amountStep).coerceAtLeast(0).toString() },
            onPlus = { amountText = ((amount ?: 0) + amountStep).toString() },
            unit = exercise.amountUnit(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            contentDescription = exercise.amountUnit(),
        )
        if (editingStep && !exercise.isWeighted) {
            Gap(12.dp)
            StepEditor(exercise.formatStep(step)) { change ->
                val next = step + change * exercise.stepIncrement()
                if (next > 0) viewModel.setStep(exercise.id, next)
            }
        }
    }
}

/** Changes how much the target grows when this exercise progresses. Saved per exercise. */
@Composable
private fun StepEditor(value: String, onChange: (Int) -> Unit) {
    val colors = HabitaTheme.colors
    HabitaCard(Modifier.fillMaxWidth(), color = colors.accentTint, borderColor = colors.accentTint) {
        HText("Progression step for this exercise", HabitaTheme.typography.caption, color = colors.muted)
        Gap(8.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("−", { onChange(-1) }, Modifier.weight(1f))
            HText(value, HabitaTheme.typography.bodyStrong, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            SecondaryButton("+", { onChange(1) }, Modifier.weight(1f))
        }
    }
}

/** One segment per set: filled when logged, outlined for the current one. */
@Composable
private fun SetProgress(done: Int, total: Int) {
    val colors = HabitaTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { index ->
            val modifier = Modifier.weight(1f).height(8.dp)
            when {
                index < done -> Box(modifier.background(colors.accent, CircleShape))
                index == done -> Box(modifier.border(1.5.dp, colors.accent, CircleShape))
                else -> Box(modifier.background(colors.border, CircleShape))
            }
        }
    }
}

/** Scrolling content with the action buttons pinned at the bottom, within thumb reach. */
@Composable
private fun ActionLayout(
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            content()
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            actions()
        }
    }
}