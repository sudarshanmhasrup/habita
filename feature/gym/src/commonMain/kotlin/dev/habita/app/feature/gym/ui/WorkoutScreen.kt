package dev.habita.app.feature.gym.ui

import dev.habita.app.core.ui.Motion
import dev.habita.app.core.ui.ContentSwap
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.habita.app.core.data.nowMillis
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.ControlShape
import dev.habita.app.core.ui.Divider
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HIcon
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.HabitaIcons
import dev.habita.app.core.ui.LocalWideLayout
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PaneGap
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
import dev.habita.app.shared.gym.Exercise
import dev.habita.app.shared.gym.Measure
import dev.habita.app.shared.gym.Progression
import dev.habita.app.shared.gym.SetAdviceReason
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        TopBar(
            "Gym",
            onBack,
            Modifier.padding(start = Space.screen, end = Space.item, top = Space.inside),
        ) {
            Elapsed(active.startedAt)
            TextAction(text = "Finish", onClick = { confirmFinish = true })
        }
        ProgressBar(
            active.progress,
            Modifier.padding(start = Space.screen, end = Space.screen, top = Space.inside, bottom = Space.tiny),
            height = 4.dp,
        )
        // Each step of the workout (a warm-up, a set, a rest) eases in as the last one leaves.
        ContentSwap(active, Modifier.weight(1f), key = { it.stepKey() }) { active ->
        val rest = active.rest
        when {
            active.phase == Phase.WARM_UP -> WarmUpContent(active, viewModel)
            active.phase == Phase.BREAK && rest != null -> RestContent(
                title = "Break",
                rest = rest,
                onDone = viewModel::endBreak,
                onExtend = { viewModel.extendRest(30) },
            ) {
                active.currentSlot?.let { NextUp(active, data, it, label = "Main workout starts with") }
            }
            rest != null -> RestContent(
                title = "Rest",
                rest = rest,
                onDone = viewModel::endRest,
                onExtend = { viewModel.extendRest(30) },
            ) {
                active.currentSlot?.let { NextUp(active, data, it, label = "Up next") }
            }
            active.currentSlot != null -> ExerciseContent(active, data, viewModel)
        }
        }
    }
    FinishPanel(
        visible = confirmFinish,
        workoutName = active.workout.name,
        onFinish = {
            confirmFinish = false
            viewModel.finish()
        },
        onKeepGoing = { confirmFinish = false },
    )
    }
}

/** What identifies one step of the workout, so moving to the next one animates. */
private fun ActiveWorkout.stepKey(): Any = listOf(phase, rest != null, currentWarmUp, currentSlot, loggedSets.size, warmUpSetsDone.size)

/** Asks before finishing, over a dimmed screen. Tapping outside keeps the workout going. */
@Composable
private fun BoxScope.FinishPanel(visible: Boolean, workoutName: String, onFinish: () -> Unit, onKeepGoing: () -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    AnimatedVisibility(visible, enter = fadeIn(tween(Motion.SHORT)), exit = fadeOut(tween(Motion.SHORT))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(interactionSource = null, indication = null, onClick = onKeepGoing),
        )
    }
    AnimatedVisibility(
        visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(Motion.MEDIUM, easing = Motion.Ease)) { it } + fadeIn(),
        exit = slideOutVertically(tween(Motion.SHORT)) { it } + fadeOut(),
    ) {
        HabitaCard(Modifier.fillMaxWidth().padding(Space.item)) {
            HText("Finish $workoutName?", type.title)
            Gap(Space.tiny)
            HText("Sets you haven't done are left out of today's numbers.", type.body, color = colors.muted)
            Gap(Space.card)
            PrimaryButton("Finish workout", onFinish, Modifier.fillMaxWidth())
            Gap(Space.inside)
            SecondaryButton("Keep going", onKeepGoing, Modifier.fillMaxWidth())
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = Space.tiny)) {
        HIcon(HabitaIcons.Clock, contentDescription = "Workout time", modifier = Modifier.size(16.dp), tint = HabitaTheme.colors.muted)
        Gap(Space.tiny)
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
            Gap(Space.inside)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.inside)) {
                SecondaryButton("Skip", viewModel::warmUpSkip, Modifier.weight(1f))
                if (active.warmUpQueue.size > 1) {
                    SecondaryButton("Machine taken", viewModel::warmUpMachineTaken, Modifier.weight(1f))
                }
            }
        },
        side = { WarmUpUpcoming(active) },
    ) {
        Overline("Warm-up · ${done + 1} of $total", color = colors.accent)
        Gap(Space.inside)
        HText(item.name, type.headline)
        Gap(Space.card)
        HabitaCard(Modifier.fillMaxWidth()) {
            Overline("Do")
            Gap(Space.inside)
            HText(item.amount, type.number)
        }
    }
}

/** The warm-up items still to come, after the current one. */
@Composable
private fun ColumnScope.WarmUpUpcoming(active: ActiveWorkout) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    Overline("Then")
    Gap(Space.item)
    val upcoming = active.warmUpQueue.drop(1)
    if (upcoming.isEmpty()) {
        HText("${active.warmUp.restSeconds / 60} min break, then the main workout", type.body, color = colors.muted)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Space.item)) {
            upcoming.forEach { index ->
                val next = active.warmUp.items[index]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HText(next.name, type.body, modifier = Modifier.weight(1f))
                    Gap(Space.item)
                    HText(next.amount, type.caption, color = colors.muted)
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
    val alert = rememberRestAlert()
    var now by remember { mutableLongStateOf(nowMillis()) }
    // Ticks until the rest is over, buzzes, then moves on. This screen leaves as soon as the rest
    // ends, so a repeat call only happens if the first one was ignored while another action ran.
    LaunchedEffect(rest) {
        var buzzed = false
        while (true) {
            now = nowMillis()
            if (rest.remainingMillis(now) == 0L) {
                if (!buzzed) {
                    // Only for a rest that ran out here, not one that ended while the app was away.
                    if (now - rest.endsAt < 5_000) alert()
                    buzzed = true
                }
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
            Row(horizontalArrangement = Arrangement.spacedBy(Space.inside)) {
                SecondaryButton("+30 s", onExtend, Modifier.weight(1f))
                PrimaryButton(if (title == "Break") "Start main" else "Skip rest", onDone, Modifier.weight(2f))
            }
        },
        side = next,
    ) {
        Column(Modifier.fillMaxWidth().padding(top = Space.item), horizontalAlignment = Alignment.CenterHorizontally) {
            Overline(title, color = colors.accent)
            Gap(Space.card)
            Box(Modifier.size(232.dp), contentAlignment = Alignment.Center) {
                val fraction = remaining.toFloat() / rest.totalMillis.coerceAtLeast(1)
                // A dial of 60 ticks: the accent for the time left, grey for the time gone.
                Canvas(Modifier.fillMaxSize()) {
                    val ticks = 60
                    val lit = (fraction * ticks).toInt()
                    val outer = size.minDimension / 2
                    val long = 18.dp.toPx()
                    val short = 12.dp.toPx()
                    for (i in 0 until ticks) {
                        val angle = (i.toFloat() / ticks) * 2f * PI.toFloat() - PI.toFloat() / 2f
                        val length = if (i % 5 == 0) long else short
                        val dx = cos(angle)
                        val dy = sin(angle)
                        drawLine(
                            color = if (i < lit) colors.accent else colors.border,
                            start = Offset(center.x + dx * (outer - length), center.y + dy * (outer - length)),
                            end = Offset(center.x + dx * outer, center.y + dy * outer),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HText(formatTimer(remaining), type.timer)
                    HText("of ${formatTimer(rest.totalMillis)}", type.caption, color = colors.muted)
                }
            }
        }
    }
}

/** What comes after the rest: the exercise, the set, and today's weight for it. */
@Composable
private fun NextUp(active: ActiveWorkout, data: GymData, slotIndex: Int, label: String) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val exercise = active.exercises[slotIndex]
    val slot = active.slot(slotIndex)
    HabitaCard(Modifier.fillMaxWidth()) {
        Overline(label)
        Gap(Space.inside)
        HText(exercise.name, type.title)
        Gap(Space.tiny)
        val line = if (active.needsWarmUpSet(slotIndex)) {
            val working = active.nextSet(slotIndex).weightGrams
            val step = data.step(exercise.id) ?: exercise.defaultStep
            "Warm-up set · " + (
                working?.let { "${formatKg(Progression.warmUpWeight(it, step))} kg × ${Progression.WARM_UP_REPS}" }
                    ?: "light × ${Progression.WARM_UP_REPS}"
                )
        } else {
            "Set ${active.setNumber(slotIndex)} of ${slot.sets} · ${active.nextSet(slotIndex).describe(exercise)}"
        }
        HText(line, type.body, color = colors.muted)
        adviceText(active, slotIndex)?.let {
            Gap(Space.item)
            HText(it, type.label, color = colors.accent)
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
    val slotIndex = active.currentSlot ?: return
    if (active.needsWarmUpSet(slotIndex)) {
        WarmUpSetContent(active, data, viewModel, slotIndex)
    } else {
        WorkingSetContent(active, data, viewModel, slotIndex)
    }
}

/** The exercise name with where it sits in the workout, shared by warm-up and working sets. */
@Composable
private fun ColumnScope.ExerciseHeader(active: ActiveWorkout, slotIndex: Int, overline: String) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val nextName = active.nextSlot?.let { active.exercises[it].name }
    Overline(overline, color = colors.accent)
    Gap(Space.inside)
    HText(active.exercises[slotIndex].name, type.headline)
    Gap(Space.tiny)
    HText(nextName?.let { "Then $it" } ?: "Last exercise", type.caption, color = colors.muted)
}

/** A lighter set before the working sets: about half the weight, not counted toward progress. */
@Composable
private fun WarmUpSetContent(active: ActiveWorkout, data: GymData, viewModel: GymViewModel, slotIndex: Int) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val exercise = active.exercises[slotIndex]
    val step = data.step(exercise.id) ?: exercise.defaultStep
    val working = active.nextSet(slotIndex).weightGrams
    val suggested = working?.let { Progression.warmUpWeight(it, step) }
    var weightText by remember(active.sessionId, slotIndex) { mutableStateOf(suggested?.let(::formatKg) ?: "") }
    var repsText by remember(active.sessionId, slotIndex) { mutableStateOf(Progression.WARM_UP_REPS.toString()) }
    val weightGrams = parseKg(weightText)
    val reps = parseCount(repsText)

    ActionLayout(
        actions = {
            PrimaryButton(
                "Done",
                onClick = { viewModel.logWarmUpSet(weightGrams!!, reps!!) },
                enabled = weightGrams != null && reps != null,
                modifier = Modifier.fillMaxWidth(),
            )
            Gap(Space.tiny)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextAction("Skip warm-up set", { viewModel.logWarmUpSet(0, null) }, color = colors.muted)
                Spacer(Modifier.weight(1f))
                if (active.exerciseQueue.size > 1) {
                    TextAction("Machine taken", viewModel::exerciseMachineTaken, color = colors.muted)
                }
            }
        },
        side = {
            SetInputs(
                exercise = exercise,
                step = step,
                weightText = weightText,
                onWeightChange = { weightText = it },
                amountText = repsText,
                onAmountChange = { repsText = it },
                onEditStep = null,
            )
        },
    ) {
        ExerciseHeader(active, slotIndex, "Exercise ${active.exercisesDone + 1} of ${active.workout.exercises.size} · warm-up set")
        Gap(Space.card)
        Column(Modifier.fillMaxWidth()) {
            HText(
                if (working != null) {
                    "About half of today's ${formatKg(working)} kg, easy reps to get the movement in. It doesn't count toward progress."
                } else {
                    "Go light, about half of what you'll lift, to get the movement in. It doesn't count toward progress."
                },
                type.body,
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun WorkingSetContent(active: ActiveWorkout, data: GymData, viewModel: GymViewModel, slotIndex: Int) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val exercise = active.exercises[slotIndex]
    val baseTarget = active.targets[slotIndex]
    val next = active.nextSet(slotIndex)
    val setNumber = active.setNumber(slotIndex)
    val step = data.step(exercise.id) ?: exercise.defaultStep
    val advice = active.advice[slotIndex]

    // Prefilled with today's target, adjusted by how the last set felt, so a normal set is one tap.
    // With no target yet (the first time), the warm-up set is about half, so double it.
    var weightText by remember(active.sessionId, slotIndex, setNumber) {
        val prefill = next.weightGrams ?: active.warmUpSetFor(slotIndex)?.weightGrams?.let { it * 2 }
        mutableStateOf(prefill?.let(::formatKg) ?: "")
    }
    var amountText by remember(active.sessionId, slotIndex, setNumber) { mutableStateOf(next.target.toString()) }
    var editingStep by remember(slotIndex) { mutableStateOf(false) }

    val weightGrams = if (exercise.isWeighted) parseKg(weightText) else 0
    val amount = parseCount(amountText)
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
            Gap(Space.inside)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.inside)) {
                Effort.entries.forEach { effort ->
                    EffortButton(
                        effort = effort,
                        enabled = canLog,
                        onClick = { viewModel.logSet(weightGrams!!, amount!!, effort) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Gap(Space.tiny)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextAction("Skip set", { viewModel.logSet(weightGrams ?: 0, null, null) }, color = colors.muted)
                Spacer(Modifier.weight(1f))
                if (active.exerciseQueue.size > 1) {
                    TextAction("Machine taken", viewModel::exerciseMachineTaken, color = colors.muted)
                }
            }
        },
        side = {
            SetInputs(
                exercise = exercise,
                step = step,
                weightText = weightText,
                onWeightChange = { weightText = it },
                amountText = amountText,
                onAmountChange = { amountText = it },
                onEditStep = { editingStep = !editingStep },
            )
            if (editingStep) {
                Gap(Space.item)
                StepEditor(exercise.formatStep(step)) { change ->
                    val nextStep = step + change * exercise.stepIncrement()
                    if (nextStep > 0) viewModel.setStep(exercise.id, nextStep)
                }
            }
        },
    ) {
        ExerciseHeader(active, slotIndex, "Exercise ${active.exercisesDone + 1} of ${active.workout.exercises.size}")
        Gap(Space.card)
        SetTrack(active, slotIndex)
        Gap(Space.card)

        val adviceLine = adviceText(active, slotIndex)
        if (adviceLine != null && advice != null) {
            HabitaCard(
                Modifier.fillMaxWidth(),
                color = colors.accentTint,
                borderColor = colors.accentTint,
                contentPadding = PaddingValues(start = Space.card, end = Space.inside, top = Space.inside, bottom = Space.inside),
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
            Gap(Space.item)
        }

        HabitaCard(Modifier.fillMaxWidth()) {
            Row {
                Column(Modifier.weight(1f)) {
                    Overline("Last time")
                    Gap(Space.tiny)
                    HText(baseTarget.lastTime?.describe(exercise) ?: "First time", type.bodyStrong)
                }
                Gap(Space.item)
                Column(Modifier.weight(1f)) {
                    Overline("Today", color = colors.accent)
                    Gap(Space.tiny)
                    HText(baseTarget.describe(exercise), type.bodyStrong)
                }
            }
            if (setNumber == 1) {
                Gap(Space.item)
                Divider()
                Gap(Space.item)
                HText(baseTarget.reasonText(exercise, step), type.caption, color = colors.muted)
            }
        }
    }
}

/**
 * The weight (for weighted exercises) and reps or seconds of the set about to be logged.
 * [onEditStep] shows the step action next to the first label; `null` hides it.
 */
@Composable
private fun ColumnScope.SetInputs(
    exercise: Exercise,
    step: Int,
    weightText: String,
    onWeightChange: (String) -> Unit,
    amountText: String,
    onAmountChange: (String) -> Unit,
    onEditStep: (() -> Unit)?,
) {
    val amountStep = if (exercise.measure == Measure.SECONDS) 5 else 1
    if (exercise.isWeighted) {
        val grams = parseKg(weightText)
        InputLabel(exercise.weightLabel(), if (onEditStep != null) "Step ${exercise.formatStep(step)}" else null, onEditStep)
        Gap(Space.tiny)
        Stepper(
            value = weightText,
            onValueChange = onWeightChange,
            onMinus = { onWeightChange(formatKg(((grams ?: 0) - step).coerceAtLeast(0))) },
            onPlus = { onWeightChange(formatKg((grams ?: 0) + step)) },
            unit = "kg",
            placeholder = "0",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            contentDescription = "weight",
        )
        Gap(Space.card)
    }
    val amount = parseCount(amountText)
    InputLabel(
        exercise.amountLabel(),
        if (!exercise.isWeighted && onEditStep != null) "Step ${exercise.formatStep(step)}" else null,
        onEditStep,
    )
    Gap(Space.tiny)
    Stepper(
        value = amountText,
        onValueChange = onAmountChange,
        onMinus = { onAmountChange(((amount ?: 0) - amountStep).coerceAtLeast(0).toString()) },
        onPlus = { onAmountChange(((amount ?: 0) + amountStep).toString()) },
        unit = exercise.amountUnit(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        contentDescription = exercise.amountUnit(),
    )
}

/** A field label with an optional small action on the right, on a fixed-height row. */
@Composable
private fun InputLabel(text: String, action: String?, onAction: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        HText(text, HabitaTheme.typography.label, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextAction(action, onAction)
    }
}

/** A big button that logs the set with how it felt. Medium, the usual answer, is filled. */
@Composable
private fun EffortButton(effort: Effort, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HabitaTheme.colors
    val filled = effort == Effort.MEDIUM
    val ink = colors.highlight
    val onInk = colors.onHighlight
    val background = if (filled) ink else colors.card
    Column(
        modifier
            .height(68.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(ControlShape)
            .background(background)
            .border(1.dp, if (filled) ink else colors.border, ControlShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Log set as ${effort.label()}", onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (filled) onInk else colors.effort(effort), CircleShape),
            )
            Gap(6.dp)
            HText(effort.label(), HabitaTheme.typography.button, color = if (filled) onInk else colors.text)
        }
        Gap(2.dp)
        HText(
            when (effort) {
                Effort.EASY -> "had more in me"
                Effort.MEDIUM -> "just right"
                Effort.HARD -> "barely made it"
            },
            HabitaTheme.typography.caption,
            color = if (filled) onInk.copy(alpha = 0.7f) else colors.muted,
        )
    }
}

/**
 * One tile per set: done sets show what was lifted, colored by how they felt; the current set is
 * outlined; later sets are quiet. A done warm-up set gets a small tile of its own in front.
 */
@Composable
private fun SetTrack(active: ActiveWorkout, slotIndex: Int) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val exercise = active.exercises[slotIndex]
    val logged = active.setsFor(slotIndex)
    val warmUpSet = active.warmUpSetFor(slotIndex)
    val total = active.slot(slotIndex).sets
    val shape = ControlShape
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.inside)) {
        if (warmUpSet != null) {
            Column(
                Modifier.weight(0.8f).height(60.dp).clip(shape).border(1.dp, colors.border, shape),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HText("Warm-up", type.caption, color = colors.muted, maxLines = 1)
                HText("${formatKg(warmUpSet.weightGrams)} × ${warmUpSet.achieved}", type.label, color = colors.muted, maxLines = 1)
            }
        }
        repeat(total) { index ->
            val set = logged.getOrNull(index)
            val current = index == logged.size
            val modifier = Modifier.weight(1f).height(60.dp).clip(shape)
            when {
                set != null -> Column(
                    modifier.background(colors.card),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(if (set.isSkipped) colors.border else colors.effort(set.effort), CircleShape))
                        Gap(Space.tiny)
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
                    modifier.border(1.5.dp, colors.accent, shape),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HText("Now", type.caption, color = colors.accent)
                    HText("Set ${index + 1}", type.bodyStrong)
                }
                else -> Box(modifier.border(1.dp, colors.border, shape), contentAlignment = Alignment.Center) {
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
    HabitaCard(Modifier.fillMaxWidth(), borderColor = colors.border) {
        HText("How much − and + move, and the jump when you progress", HabitaTheme.typography.caption, color = colors.muted)
        Gap(Space.item)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.item)) {
            SecondaryButton("−", { onChange(-1) }, Modifier.weight(1f))
            HText(value, HabitaTheme.typography.bodyStrong, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            SecondaryButton("+", { onChange(1) }, Modifier.weight(1f))
        }
    }
}

/**
 * Scrolling content with the action buttons pinned at the bottom, within thumb reach. On phones
 * [side] follows [content] in the same scroll; on wide screens it gets the right-hand pane, with
 * the actions under it.
 */
@Composable
private fun ActionLayout(
    actions: @Composable ColumnScope.() -> Unit,
    side: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val padding = Modifier.padding(start = Space.screen, end = Space.screen, top = Space.card, bottom = Space.card)
    val actionPadding = Modifier.padding(start = Space.screen, end = Space.screen, top = Space.item, bottom = Space.item)
    if (LocalWideLayout.current) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(PaneGap)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).then(padding), content = content)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).then(padding), content = side)
                Column(actionPadding, content = actions)
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).then(padding)) {
                content()
                Gap(Space.section)
                side()
            }
            Column(actionPadding, content = actions)
        }
    }
}