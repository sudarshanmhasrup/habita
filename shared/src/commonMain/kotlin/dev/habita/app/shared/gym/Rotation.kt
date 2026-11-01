package dev.habita.app.shared.gym

/**
 * Picks the next workout by rotation, not by weekday: the one after the last finished rotation
 * workout, whatever day it is. Fallback workouts do not move the rotation.
 */
object Rotation {
    fun next(program: Program, sessions: List<WorkoutSession>): Workout {
        val order = program.workouts.filter { it.rotation != null }.sortedBy { it.rotation }
        val orderIds = order.map { it.id }
        val last = sessions
            .filter { !it.deleted && it.endedAt != null && it.workoutId in orderIds }
            .maxByOrNull { it.endedAt!! }
            ?: return order.first()
        return order[(orderIds.indexOf(last.workoutId) + 1) % order.size]
    }
}