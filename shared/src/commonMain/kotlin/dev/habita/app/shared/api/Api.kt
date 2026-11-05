package dev.habita.app.shared.api

import dev.habita.app.shared.gym.ExerciseStep
import dev.habita.app.shared.gym.ExerciseTarget
import dev.habita.app.shared.gym.Program
import dev.habita.app.shared.gym.SetLog
import dev.habita.app.shared.gym.WarmUpLog
import dev.habita.app.shared.gym.WorkoutSession
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val token: String,
)

@Serializable
data class ProgramResponse(
    val version: Long,
    val program: Program,
)

/** Every gym record, as the gym's backup file and the server export hold them. */
@Serializable
data class SyncChanges(
    val sessions: List<WorkoutSession> = emptyList(),
    val sets: List<SetLog> = emptyList(),
    val warmUps: List<WarmUpLog> = emptyList(),
    val steps: List<ExerciseStep> = emptyList(),
)

@Serializable
data class NextWorkoutResponse(
    val workoutId: String,
)

@Serializable
data class WorkoutTargetsResponse(
    val workoutId: String,
    val targets: List<ExerciseTarget>,
)

@Serializable
data class ExportedProgram(
    val version: Long,
    val content: String,
    val createdAt: Long,
)

/**
 * Everything in one file. [changes] holds the gym; [records] every other synced collection, such
 * as expenses, keyed by collection name.
 */
@Serializable
data class Export(
    val exportedAt: Long,
    val programs: List<ExportedProgram>,
    val changes: SyncChanges,
    val records: Map<String, List<SyncRecord>> = emptyMap(),
)