package dev.habita.server.routes

import dev.habita.app.shared.api.NextWorkoutResponse
import dev.habita.app.shared.api.ProgramResponse
import dev.habita.app.shared.api.WorkoutTargetsResponse
import dev.habita.app.shared.gym.History
import dev.habita.app.shared.gym.Progression
import dev.habita.app.shared.gym.Rotation
import dev.habita.app.shared.gym.Summary
import dev.habita.server.ErrorResponse
import dev.habita.server.data.LoadedProgram
import dev.habita.server.data.Store
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.gymRoutes(loaded: LoadedProgram, store: Store, clock: () -> Long) {
    val program = loaded.program

    get("/program") {
        call.respond(ProgramResponse(loaded.version, program))
    }

    get("/workouts/next") {
        call.respond(NextWorkoutResponse(Rotation.next(program, store.all().sessions).id))
    }

    get("/workouts/{id}/targets") {
        val workout = program.workout(call.parameters["id"]!!)
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Unknown workout"))
        val all = store.all()
        val steps = all.steps.associate { it.exerciseId to it.step }
        val now = clock()
        val targets = workout.exercises.map { slot ->
            val exercise = program.exercise(slot.exerciseId)!!
            Progression.target(
                exercise = exercise,
                slot = slot,
                step = steps[exercise.id] ?: exercise.defaultStep,
                history = History.performances(exercise.id, all.sessions, all.sets),
                now = now,
            )
        }
        call.respond(WorkoutTargetsResponse(workout.id, targets))
    }

    get("/sessions/{id}/summary") {
        val session = store.session(call.parameters["id"]!!)?.takeIf { !it.deleted }
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Unknown session"))
        val all = store.all()
        val summary = Summary.of(
            session = session,
            program = program,
            sessions = all.sessions,
            sets = all.sets,
            steps = all.steps.associate { it.exerciseId to it.step },
            now = clock(),
        )
        call.respond(summary)
    }
}