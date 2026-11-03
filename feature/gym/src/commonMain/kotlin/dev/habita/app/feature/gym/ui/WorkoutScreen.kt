package dev.habita.app.feature.gym.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.habita.app.core.data.nowMillis
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.ui.ControlShape
import dev.habita.app.core.ui.Divider
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HIcon
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.HabitaIcons
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.ProgressBar
import dev.habita.app.core.ui.SecondaryButton
import dev.habita.app.core.ui.Stepper
import dev.habita.app.core.ui.TextAction
import dev.habita.app.core.ui.TopBar
import dev.habita.app.feature.gym.workout.ActiveWorkout
import dev.habita.app.feature.gym.workout.Phase
import dev.habita.app.feature.gym.workout.Rest
import dev.habita.app.shared.gym.Effort
import dev.habita.app.shared.gym.Measure
import dev.habita.app.shared.gym.SetAdviceReason
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
    val colors = HabitaTheme.colors
    var confirmFinish by remember(active.sessionId) { mutableStateOf(false) }
    LaunchedEffect(confirmFinish) {
        if (confirmFinish) {
            delay(4000)
            confirmFinish = false
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp)) {
            TopBar("Gym", onBack) {
                Elapsed(active.startedAt)
                TextAction(
                    text = if (confirmFinish) "Tap again to finish" else "Finish",
                    onClick = { if (confirmFinish) viewModel.finish() else confirmFinish = true },
                    color = if (confirmFinish) colors.error else colors.accent,
                )
            }
        }
        ProgressBar(active.progress, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), height = 4.dp)
        val rest = active.rest
        when {
            active.phase == Phase.WARM_UP -> WarmUpContent(active, viewModel)
            active.phase == Phase.BREAK && rest != null -> RestContent(
                title = "Break",
                rest = rest,
                onDone = viewModel::endBreak,
                onExtend = { viewModel.extendRest(30) },
            ) {
                active.currentSlot?.let { NextUp(active, it, label = "Main workout starts with") }
            }
            rest != null -> RestContent(
                title = "Rest",
                rest = rest,
                onDone = viewModel::endRest,
                onExtend = { viewModel.extendRest(30) },
            ) {
                active.currentSlot?.let { NextUp(active, it, label = "Up next") }
            }
            active.currentSlot != null -> ExerciseContent(active, data, viewModel)
        }
    }
}

/** Time since the workout started, ticking every second. */
@Composable
private fun Elapsed(startedAt: Long) {
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = nowMillis()
            delay(1000)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
        HIcon(HabitaIcons.Clock, contentDescription = "Workout time", modifier = Modifier.size(16.dp), tint = HabitaTheme.colors.muted)
        Gap(4.dp)
        HText(formatTimer(now - startedAt), HabitaTheme.typography.label, color = HabitaTheme.colors.muted)
    }
}

@Composable
private fun WarmUpContent(active: ActiveWorkout, viewModel: GymViewModel) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val position = active.currentWarmUp ?: return
    val item = active.warmUp.items[position]
    val total = active.warmUp.items.size
    val done = total - active.warmUpQueue.size
    ActionLayout(
        actions = {
            PrimaryButton("Done", viewModel::warmUpDone, Modifier.fillMaxWidth())
            Gap(10.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Skip", viewModel::warmUpSkip, Modifier.weight(1f))
                if (active.warmUpQueue.size > 1) {
                    SecondaryButton("Machine taken", viewModel::warmUpMachineTaken, Modifier.weight(1f))
                }
            }
        },
    ) {
        Overline("Warm-up · ${done + 1} of $total", color = colors.accent)
        Gap(8.dp)
        HText(item.name, type.headline)
        Gap(20.dp)
        HabitaCard(Modifier.fillMaxWidth(), color = colors.accentTint, borderColor = colors.accentTint) {
            Overline("Do", color = colors.accent)
            Gap(6.dp)
            HText(item.amount, type.number)
        }
        Gap(24.dp)
        Overline("Then")
        Gap(10.dp)
        val upcoming = active.warmUpQueue.drop(1)
        if (upcoming.isEmpty()) {
            HText("${active.warmUp.restSeconds / 60} min break, then the main workout", type.body, color = colors.muted)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                upcoming.forEach { index ->
                    val next = active.warmUp.items[index]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(colors.border, CircleShape))
                        Gap(12.dp)
                        HText(next.name, type.body, modifier = Modifier.weight(1f))
                        HText(next.amount, type.caption, color = colors.muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun RestContent(
    title: String,
    rest: Rest,
    onDone: () -> Unit,
    onExtend: () -> Unit,
    next: @Composable ColumnScope.() -> Unit,
) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("+30 s", onExtend, Modifier.weight(1f))
                PrimaryButton(if (title == "Break") "Start main" else "Skip rest", onDone, Modifier.weight(2f))
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Overline(title, color = colors.accent)
            Gap(16.dp)
            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                val fraction = remaining.toFloat() / rest.totalMillis.coerceAtLeast(1)
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 12.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(inset, inset)
                    drawArc(colors.border, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                    drawArc(colors.accent, -90f, 360f * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HText(formatTimer(remaining), type.timer)
                    HText("of ${formatTimer(rest.totalMillis)}", type.caption, color = colors.muted)
                }
            }
        }
        Gap(28.dp)
        next()
    }
}

/** What comes after the rest: the exercise, the set, and today's weight for it. */
@Composable
private fun NextUp(active: ActiveWorkout, slotIndex: Int, label: String) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val exercise = active.exercises[slotIndex]
    val slot = active.slot(slotIndex)
    HabitaCard(Modifier.fillMaxWidth()) {
        Overline(label)
        Gap(6.dp)
        HText(exercise.name, type.title)
        Gap(2.dp)
        HText(
            "Set ${active.setNumber(slotIndex)} of ${slot.sets} · ${active.nextSet(slotIndex).describe(exercise)}",
            type.body,
            color = colors.muted,
        )
        adviceText(active, slotIndex)?.let {
            Gap(10.dp)
            HText(it, type.caption, color = colors.accent)
        }
    }
}

/** Why the next set's weight changed, or `null` when it did not. */
private fun adviceText(active: ActiveWorkout, slotIndex: Int): String? {
    val advice = active.advice[slotIndex] ?: return null
    val exercise = active.exercises[slotIndex]
    val previous = active.setsFor(slotIndex).lastOrNull { !it.isSkipped } ?: return null
    if (!exercise.isWeighted) {
        return when (advice.reason) {
            SetAdviceReason.EASY -> "Last set felt easy, so this one is ${advice.target} s."
            else -> null
        }
    }
    // The weight typed for the last set already moved away from the advice's starting point.
    if (previous.weightGrams != advice.previousWeightGrams) return null
    return when (advice.reason) {
        SetAdviceReason.EASY -> "Last set felt easy: up to ${formatKg(advice.weightGrams)} kg."
        SetAdviceReason.TOO_HARD -> "Last set was a grind: down to ${formatKg(advice.weightGrams)} kg."
        SetAdviceReason.KEEP -> null
    }
}

@Composable
private fun ExerciseContent(active: ActiveWorkout, data: GymData, viewModel: GymViewModel) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val slotIndex = active.currentSlot ?: return
    val slot = active.slot(slotIndex)
    val exercise = active.exercises[slotIndex]
    val baseTarget = active.targets[slotIndex]
    val next = active.nextSet(slotIndex)
    val setNumber = active.setNumber(slotIndex)
    val step = data.step(exercise.id) ?: exercise.defaultStep
    val advice = active.advice[slotIndex]

    // Prefilled with today's target, adjusted by how the last set felt, so a normal set is one tap.
    var weightText by remember(active.sessionId, slotIndex, setNumber) {
        mutableStateOf(next.weightGrams?.let(::formatKg) ?: "")
    }
    var amountText by remember(active.sessionId, slotIndex, setNumber) { mutableStateOf(next.target.toString()) }
    var editingStep by remember(slotIndex) { mutableStateOf(false) }

    val weightGrams = if (exercise.isWeighted) parseKg(weightText) else 0
    val amount = parseCount(amountText)
    val amountStep = if (exercise.measure == Measure.SECONDS) 5 else 1
    val nextName = active.nextSlot?.let { active.exercises[it].name }
    val canLog = weightGrams != null && amount != null

    ActionLayout(
        actions = {
            HText(
                when {
                    canLog -> "Done? Tap how it felt"
                    weightGrams == null -> "Enter the weight you're using"
                    else -> "Enter how many ${exercise.amountUnit()} you did"
                },
                type.caption,
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Effort.entries.forEach { effort ->
                    EffortButton(
                        effort = effort,
                        enabled = canLog,
                        onClick = { viewModel.logSet(weightGrams!!, amount!!, effort) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Gap(6.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextAction("Skip set", { viewModel.logSet(weightGrams ?: 0, null, null) }, color = colors.muted)
                Spacer(Modifier.weight(1f))
                if (active.exerciseQueue.size > 1) {
                    TextAction("Machine taken", viewModel::exerciseMachineTaken, color = colors.muted)
                }
            }
        },
    ) {
        Overline("Exercise ${active.exercisesDone + 1} of ${active.workout.exercises.size}", color = colors.accent)
        Gap(6.dp)
        HText(exercise.name, type.headline)
        Gap(4.dp)
        HText(
            nextName?.let { "Next: $it" } ?: "Last exercise",
            type.caption,
            color = colors.muted,
        )
        Gap(16.dp)
        SetTrack(active, slotIndex)
        Gap(16.dp)

        val adviceLine = adviceText(active, slotIndex)
        if (adviceLine != null && advice != null) {
            HabitaCard(
                Modifier.fillMaxWidth(),
                color = colors.accentTint,
                borderColor = colors.accentTint,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HText(adviceLine, type.label, modifier = Modifier.weight(1f))
                    if (exercise.isWeighted && advice.weightGrams != advice.previousWeightGrams) {
                        TextAction(
                            "Keep ${formatKg(advice.previousWeightGrams)} kg",
                            { weightText = formatKg(advice.previousWeightGrams) },
                        )
                    }
                }
            }
            Gap(12.dp)
        }

        HabitaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Overline("Last time")
                    Gap(4.dp)
                    HText(baseTarget.lastTime?.describe(exercise) ?: "First time", type.bodyStrong)
                }
                Gap(12.dp)
                Column(Modifier.weight(1f)) {
                    Overline("Today", color = colors.accent)
                    Gap(4.dp)
                    HText(baseTarget.describe(exercise), type.bodyStrong)
                }
            }
            if (setNumber == 1) {
                Gap(12.dp)
                Divider()
                Gap(10.dp)
                HText(baseTarget.reasonText(exercise, step), type.caption, color = colors.muted)
            }
        }
        Gap(20.dp)
        if (exercise.isWeighted) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HText(exercise.weightLabel(), type.label, modifier = Modifier.weight(1f))
                TextAction("± ${exercise.formatStep(step)}", { editingStep = !editingStep })
            }
            Gap(6.dp)
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
                Gap(10.dp)
                StepEditor(exercise.formatStep(step)) { change ->
                    val nextStep = step + change * exercise.stepIncrement()
                    if (nextStep > 0) viewModel.setStep(exercise.id, nextStep)
                }
            }
            Gap(16.dp)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HText(exercise.amountLabel(), type.label, modifier = Modifier.weight(1f))
            if (!exercise.isWeighted) TextAction("± ${exercise.formatStep(step)}", { editingStep = !editingStep })
        }
        Gap(6.dp)
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
            Gap(10.dp)
            StepEditor(exercise.formatStep(step)) { change ->
                val nextStep = step + change * exercise.stepIncrement()
                if (nextStep > 0) viewModel.setStep(exercise.id, nextStep)
            }
        }
    }
}

/** A big button that logs the set with how it felt. Medium, the usual answer, is filled. */
@Composable
private fun EffortButton(effort: Effort, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HabitaTheme.colors
    val filled = effort == Effort.MEDIUM
    val background = if (filled) colors.accent else colors.card
    Column(
        modifier
            .height(64.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(ControlShape)
            .background(background)
            .border(1.dp, if (filled) colors.accent else colors.border, ControlShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Log set as ${effort.label()}", onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (filled) colors.onAccent else colors.effort(effort), CircleShape),
            )
            Gap(6.dp)
            HText(effort.label(), HabitaTheme.typography.button, color = if (filled) colors.onAccent else colors.text)
        }
        HText(
            when (effort) {
                Effort.EASY -> "had more in me"
                Effort.MEDIUM -> "just right"
                Effort.HARD -> "barely made it"
            },
            HabitaTheme.typography.caption,
            color = if (filled) colors.onAccent.copy(alpha = 0.8f) else colors.muted,
        )
    }
}

/**
 * One tile per set: done sets show what was lifted, colored by how they felt; the current set is
 * outlined; later sets are quiet.
 */
@Composable
private fun SetTrack(active: ActiveWorkout, slotIndex: Int) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val exercise = active.exercises[slotIndex]
    val logged = active.setsFor(slotIndex)
    val total = active.slot(slotIndex).sets
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { index ->
            val set = logged.getOrNull(index)
            val current = index == logged.size
            val modifier = Modifier.weight(1f).height(58.dp)
            when {
                set != null -> Column(
                    modifier.clip(shape).background(colors.accentTint).padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(if (set.isSkipped) colors.border else colors.effort(set.effort), CircleShape))
                        Gap(4.dp)
                        HText(set.effort?.label() ?: if (set.isSkipped) "Skipped" else "Done", type.caption, color = colors.muted, maxLines = 1)
                    }
                    HText(
                        when {
                            set.isSkipped -> "–"
                            exercise.isWeighted -> "${formatKg(set.weightGrams)} × ${set.achieved}"
                            else -> exercise.formatAmount(set.achieved ?: 0)
                        },
                        type.bodyStrong,
                        maxLines = 1,
                    )
                }
                current -> Column(
                    modifier.clip(shape).border(2.dp, colors.accent, shape).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    HText("Now", type.caption, color = colors.accent)
                    HText("Set ${index + 1}", type.bodyStrong)
                }
                else -> Column(
                    modifier.clip(shape).border(1.dp, colors.border, shape).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    HText("Set ${index + 1}", type.bodyStrong, color = colors.muted)
                }
            }
        }
    }
}

/** Changes how much the target grows when this exercise progresses. Saved per exercise. */
@Composable
private fun StepEditor(value: String, onChange: (Int) -> Unit) {
    val colors = HabitaTheme.colors
    HabitaCard(Modifier.fillMaxWidth(), color = colors.accentTint, borderColor = colors.accentTint) {
        HText("How much +/− moves, and the jump when you progress", HabitaTheme.typography.caption, color = colors.muted)
        Gap(8.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("−", { onChange(-1) }, Modifier.weight(1f))
            HText(value, HabitaTheme.typography.bodyStrong, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            SecondaryButton("+", { onChange(1) }, Modifier.weight(1f))
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            content()
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)) {
            actions()
        }
    }
}