package dev.habita.app.shared.gym

const val DAY = 24L * 60 * 60 * 1000
const val KG = 1000

val benchPress = Exercise("bench", "Bench", Equipment.DUMBBELL, Measure.REPS, perSide = false, defaultStep = 2 * KG)
val legPress = Exercise("leg-press", "Leg Press", Equipment.MACHINE, Measure.REPS, perSide = false, defaultStep = 5 * KG)
val plank = Exercise("plank", "Plank", Equipment.BODYWEIGHT, Measure.SECONDS, perSide = false, defaultStep = 5)

val benchSlot = WorkoutExercise("bench", sets = 3, target = 12, restSeconds = 90)
val legPressSlot = WorkoutExercise("leg-press", sets = 3, target = 12, restSeconds = 90)
val plankSlot = WorkoutExercise("plank", sets = 3, target = 30, restSeconds = 60)

val testProgram = Program(
    exercises = listOf(benchPress, legPress, plank),
    warmUps = listOf(WarmUp("upper", "Upper", listOf(WarmUpItem("Walk", "5 min")), restSeconds = 120)),
    workouts = listOf(
        Workout("a", "A", "upper", rotation = 1, exercises = listOf(benchSlot, plankSlot)),
        Workout("b", "B", "upper", rotation = 2, exercises = listOf(legPressSlot)),
        Workout("c", "C", "upper", rotation = 3, exercises = listOf(benchSlot)),
        Workout("full", "Full", "upper", rotation = null, exercises = listOf(benchSlot, legPressSlot)),
    ),
)

fun session(id: String, workoutId: String, startedAt: Long, endedAt: Long? = startedAt + 60 * 60 * 1000) =
    WorkoutSession(
        id = id,
        workoutId = workoutId,
        programVersion = 1,
        startedAt = startedAt,
        mainStartedAt = startedAt + 15 * 60 * 1000,
        endedAt = endedAt,
        updatedAt = startedAt,
    )

/** Sets of one exercise in one session; a `null` result is a skipped set. */
fun sets(sessionId: String, exerciseId: String, at: Long, weightGrams: Int, target: Int, vararg results: Int?) =
    results.mapIndexed { index, achieved ->
        SetLog(
            id = "$sessionId-$exerciseId-$index",
            sessionId = sessionId,
            exerciseId = exerciseId,
            setNumber = index + 1,
            weightGrams = weightGrams,
            target = target,
            achieved = achieved,
            completedAt = at + index * 1000,
            updatedAt = at,
        )
    }

fun performance(sessionId: String, at: Long, weightGrams: Int, target: Int, vararg results: Int?) =
    Performance(sessionId, at, sets(sessionId, "any", at, weightGrams, target, *results))