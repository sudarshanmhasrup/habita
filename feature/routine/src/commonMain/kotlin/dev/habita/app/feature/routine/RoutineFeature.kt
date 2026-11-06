package dev.habita.app.feature.routine

import dev.habita.app.core.Core
import dev.habita.app.core.data.nowMillis
import dev.habita.app.feature.routine.data.Checklist
import dev.habita.app.feature.routine.data.Day
import dev.habita.app.feature.routine.data.RoutineRepository
import dev.habita.app.feature.routine.data.dayOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The daily routine: a checklist of tasks in groups such as Morning and Office, unticked again
 * every midnight. Create it once at startup.
 */
class RoutineFeature(core: Core) {
    internal val repository = RoutineRepository(core)

    /**
     * Today, moving to the next day at midnight. It checks every minute rather than sleeping
     * until midnight, so a laptop waking up the next morning still moves on.
     */
    internal val day: Flow<Day> = flow {
        while (true) {
            val day = dayOf(nowMillis())
            emit(day)
            delay((day.endsAt - nowMillis()).coerceIn(1_000, 60_000))
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    internal val checklist: Flow<Checklist> = day.flatMapLatest { day ->
        combine(repository.groups, repository.tasks, repository.doneOn(day.key)) { groups, tasks, done ->
            Checklist.of(day, groups, tasks, done)
        }
    }

    /** Today at a glance, for the home screen. */
    val today: Flow<RoutineToday> = checklist.map { list ->
        val next = list.next
        RoutineToday(
            done = list.done,
            total = list.total,
            nextTaskId = next?.second?.id,
            nextTask = next?.second?.title,
            nextGroup = next?.first?.name,
        )
    }

    /** Ticks [taskId] for today, such as from the home screen. */
    suspend fun markDone(taskId: String) {
        repository.setDone(day.first().key, taskId, true)
    }
}

/** Today's routine: how much is done, and the next task when there is one. */
data class RoutineToday(
    val done: Int,
    val total: Int,
    val nextTaskId: String?,
    val nextTask: String?,
    val nextGroup: String?,
)