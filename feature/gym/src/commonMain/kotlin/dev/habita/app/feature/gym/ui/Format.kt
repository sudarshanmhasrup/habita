package dev.habita.app.feature.gym.ui

import dev.habita.app.shared.gym.Equipment
import dev.habita.app.shared.gym.Exercise
import dev.habita.app.shared.gym.ExerciseTarget
import dev.habita.app.shared.gym.Measure
import dev.habita.app.shared.gym.Performance
import dev.habita.app.shared.gym.TargetReason
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** 22500 → "22.5", 20000 → "20", 1250 → "1.25". */
internal fun formatKg(grams: Int): String {
    val whole = grams / 1000
    val fraction = (grams % 1000).toString().padStart(3, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$whole" else "$whole.$fraction"
}

/** "22.5" or "22,5" → 22500. `null` for anything that is not a weight. */
internal fun parseKg(text: String): Int? {
    val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (value < 0 || value > 1000) return null
    return (value * 1000).roundToInt()
}

internal fun parseCount(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 0..9999 }

/** 90000 → "1:30". */
internal fun formatTimer(millis: Long): String {
    val seconds = (millis + 999) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** 3_900_000 → "1 h 5 min", 540_000 → "9 min". */
internal fun formatDuration(millis: Long): String {
    val minutes = (millis / 60_000.0).roundToLong().coerceAtLeast(0)
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

/** 4320.0 → "4,320". */
internal fun formatVolume(kg: Double): String =
    kg.roundToLong().toString().reversed().chunked(3).joinToString(",").reversed()

internal fun Exercise.amountUnit(): String = if (measure == Measure.SECONDS) "s" else "reps"

internal fun Exercise.amountLabel(): String = when {
    measure == Measure.SECONDS -> "Seconds"
    perSide -> "Reps each side"
    else -> "Reps"
}

internal fun Exercise.weightLabel(): String =
    if (equipment == Equipment.DUMBBELL) "Weight per dumbbell" else "Weight"

/** "12" or "30 s", with "each side" where it applies. */
internal fun Exercise.formatAmount(amount: Int): String = when {
    measure == Measure.SECONDS -> "$amount s"
    perSide -> "$amount each side"
    else -> "$amount"
}

/** The progression step as shown: "2 kg" for weights, "5 s" or "1 rep" for bodyweight. */
internal fun Exercise.formatStep(step: Int): String = when {
    isWeighted -> "${formatKg(step)} kg"
    measure == Measure.SECONDS -> "$step s"
    else -> if (step == 1) "1 rep" else "$step reps"
}

/** How far one tap moves the step: 0.5 kg for weights, 1 for bodyweight. */
internal fun Exercise.stepIncrement(): Int = if (isWeighted) 500 else 1

/** Today's target for one set, such as "22.5 kg × 12" or "45 s". */
internal fun ExerciseTarget.describe(exercise: Exercise): String {
    val amount = exercise.formatAmount(target)
    val weight = weightGrams
    return when {
        !exercise.isWeighted -> amount
        weight == null -> "Your pick × $amount"
        else -> "${formatKg(weight)} kg × $amount"
    }
}

/** Last time's sets, such as "20 kg × 12, 12, 10" or "30 s, 30 s, –". */
internal fun Performance.describe(exercise: Exercise): String {
    val results = sets.map { set -> set.achieved?.let { exercise.formatAmount(it) } ?: "–" }
    if (!exercise.isWeighted) return results.joinToString(", ")
    val weights = sets.filter { !it.isSkipped }.map { it.weightGrams }.distinct()
    return if (weights.size <= 1) {
        "${formatKg(weights.firstOrNull() ?: 0)} kg × ${results.joinToString(", ")}"
    } else {
        sets.joinToString(", ") { set ->
            set.achieved?.let { "${formatKg(set.weightGrams)} kg × ${exercise.formatAmount(it)}" } ?: "–"
        }
    }
}

/** Why today's target is what it is. */
internal fun ExerciseTarget.reasonText(exercise: Exercise, step: Int): String = when (reason) {
    TargetReason.FIRST_TIME ->
        if (exercise.isWeighted) "First time. Pick a weight you can lift for every rep with good form." else "First time. Aim for the target."
    TargetReason.INCREASE -> "Every set hit the target last time, so it's up ${exercise.formatStep(step)}."
    TargetReason.INCREASE_EASY -> "Every set hit the target and felt easy, so it's up two steps."
    TargetReason.FELT_EASY -> "Missed a few reps, but it all felt easy, so it's up ${exercise.formatStep(step)}."
    TargetReason.HOLD_HARD -> "You hit every rep, but it was hard. Same again to own it."
    TargetReason.REPEAT ->
        if (exercise.isWeighted) "Same weight as last time. Beat last time's reps." else "Same target. Hit it on every set."
    TargetReason.DROP_BACK -> "Reps fell too low after the last jump, so it's back to the previous weight."
    TargetReason.DELOAD -> "No progress for 3 sessions. About 10% lighter, then build back up."
    TargetReason.RETURN_AFTER_BREAK -> "Over 3 weeks since last time. Same as before, no increase."
}