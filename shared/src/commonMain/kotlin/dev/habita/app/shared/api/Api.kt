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

/**
 * A batch of records, used both to push local changes and to pull server changes.
 */
@Serializable
data class SyncChanges(
    val sessions: List<WorkoutSession> = emptyList(),
    val sets: List<SetLog> = emptyList(),
    val warmUps: List<WarmUpLog> = emptyList(),
    val steps: List<ExerciseStep> = emptyList(),
)

/**
 * [cursor] is the server position to pass as `since` on the next pull.
 */
@Serializable
data class PushResponse(
    val cursor: Long,
)

@Serializable
data class PullResponse(
    val cursor: Long,
    val changes: SyncChanges,
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

@Serializable
data class Export(
    val exportedAt: Long,
    val programs: List<ExportedProgram>,
    val changes: SyncChanges,
)