package dev.habita.app.feature.routine

import dev.habita.app.core.Core
import dev.habita.app.feature.routine.data.Checklist
import dev.habita.app.feature.routine.data.RoutineRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * The routine: a checklist of Fixed and Adjustable sections, ticked off until it is reset by
 * hand. Create it once at startup.
 */
class RoutineFeature(core: Core) {
    internal val repository = RoutineRepository(core)

    /** The current round of ticks, until the next Reset. */
    @OptIn(ExperimentalCoroutinesApi::class)
    internal val checklist: Flow<Checklist> = repository.period.distinctUntilChanged().flatMapLatest { period ->
        combine(repository.groups, repository.tasks, repository.doneOn(period.key)) { groups, tasks, done ->
            Checklist.of(period, groups, tasks, done)
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

    /** Ticks [taskId] in the current round, such as from the home screen. */
    suspend fun markDone(taskId: String) {
        repository.setDone(repository.period.first().key, taskId, true)
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