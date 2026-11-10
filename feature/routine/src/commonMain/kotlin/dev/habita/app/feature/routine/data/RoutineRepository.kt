package dev.habita.app.feature.routine.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.habita.app.core.Core
import dev.habita.app.core.data.newId
import dev.habita.app.core.data.nowMillis
import dev.habita.app.feature.routine.db.RoutineDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The routine's groups and tasks, and which tasks were done on each day, kept on the device and
 * synced when the server is reachable. Every change records its time, marks the record dirty and
 * asks for a sync.
 */
internal class RoutineRepository(core: Core) {
    private val sync = core.sync
    private val database = RoutineDatabase(core.databases.open("routine.db", RoutineDatabase.Schema))
    private val groupQueries = database.routineGroupQueries
    private val taskQueries = database.routineTaskQueries
    private val checkQueries = database.routineCheckQueries
    private val valueQueries = database.routineValueQueries

    init {
        // A new install starts with the built-in routine. It counts as never edited (time 0, not
        // dirty), so the routine as edited on another device wins once it syncs.
        database.transaction {
            // Ticks used to start over every midnight; now they last until Reset. The round in
            // progress is today's, so today's ticks carry over. Like the built-in routine, it
            // counts as never set, so a reset on any device wins over it.
            if (valueQueries.byKey(PERIOD).executeAsOneOrNull() == null) {
                val today = dayOf(nowMillis())
                valueQueries.save(PERIOD, Period(today.key, today.endsAt - DAY_MILLIS).encode(), 0, 0)
            }
            if (groupQueries.count().executeAsOne() == 0L) {
                Defaults.groups.forEach { groupQueries.save(it.id, it.name, it.position, it.days.toLong(), 0, 0, 0) }
                Defaults.tasks.forEach { taskQueries.save(it.id, it.groupId, it.title, if (it.adjustable) 1 else 0, it.position, 0, 0, 0) }
            }
        }
        routineSyncTables(database).forEach(sync::register)
    }

    private fun convertToSections() {
        if (database.convertToSections()) sync.request()
    }

    val groups: Flow<List<RoutineGroup>> = groupQueries.active { id, name, position, days, _, _, _ ->
        RoutineGroup(id, name, position, days.toInt())
    }.asFlow().mapToList(Dispatchers.Default)

    val tasks: Flow<List<RoutineTask>> = taskQueries.active { id, groupId, title, adjustable, position, _, _, _ ->
        RoutineTask(id, groupId, title, adjustable == 1L, position)
    }.asFlow().mapToList(Dispatchers.Default)

    init {
        // After the flows above exist: the old blocks (Morning, Office, ...) become sections, now
        // or whenever a device that hasn't updated yet syncs some in.
        core.scope.launch {
            groups.collect { list -> if (list.any { !Sections.isSection(it) }) convertToSections() }
        }
    }

    /** The current round of ticks, which lasts until [reset]. */
    val period: Flow<Period> = valueQueries.byKey(PERIOD).asFlow().mapToOneOrNull(Dispatchers.Default).map { row ->
        row?.let { Period.decode(it.value_) } ?: dayOf(nowMillis()).let { Period(it.key, it.endsAt - DAY_MILLIS) }
    }

    /**
     * Starts a new round: every task is unticked, on every device. Returns the round it ended,
     * whose ticks are kept, so [resume] can bring it back.
     */
    suspend fun reset(): Period {
        val ended = period.first()
        change {
            val now = nowMillis()
            valueQueries.save(PERIOD, Period("r-${newId()}", now).encode(), now, 1)
        }
        return ended
    }

    /** Makes [round] the current one again, with the ticks it had, on every device. */
    suspend fun resume(round: Period) = change {
        valueQueries.save(PERIOD, round.encode(), nowMillis(), 1)
    }

    /** The ids of the tasks done on [day]. */
    fun doneOn(day: String): Flow<Set<String>> = checkQueries.doneOn(day).asFlow().mapToList(Dispatchers.Default).map { it.toSet() }

    suspend fun setDone(day: String, taskId: String, done: Boolean) = change {
        checkQueries.save("$day/$taskId", day, taskId, if (done) 1 else 0, nowMillis(), 1)
    }

    /**
     * Saves the order dragged into place: every section in turn, each with its tasks. A task
     * dragged into another section takes that section's kind. Only what changed is written.
     */
    suspend fun saveOrder(order: List<Pair<RoutineGroup, List<RoutineTask>>>) = change {
        database.transaction {
            order.forEachIndexed { sectionIndex, (group, sectionTasks) ->
                if (group.position != sectionIndex.toLong()) writeGroup(group.copy(position = sectionIndex.toLong()))
                sectionTasks.forEachIndexed { index, task ->
                    val placed = task.copy(groupId = group.id, position = index.toLong(), adjustable = Sections.isAdjustable(group))
                    if (placed != task) writeTask(placed)
                }
            }
        }
    }

    /** Adds a Fixed or Adjustable section at the end, or changes one's kind and its tasks with it. */
    suspend fun saveSection(id: String?, adjustable: Boolean, tasks: List<RoutineTask>) = change {
        database.transaction {
            val name = if (adjustable) Sections.ADJUSTABLE else Sections.FIXED
            val old = id?.let { groupQueries.byId(it).executeAsOneOrNull() }
            if (old == null) {
                writeGroup(RoutineGroup(newId(), name, groupQueries.nextPosition().executeAsOne() ?: 0, Days.EVERY_DAY))
            } else {
                writeGroup(RoutineGroup(old.id, name, old.position, Days.EVERY_DAY))
                tasks.filter { it.groupId == old.id && it.adjustable != adjustable }.forEach { writeTask(it.copy(adjustable = adjustable)) }
            }
        }
    }

    /** Saves a new task ([RoutineTask.id] blank) at the end of its group, or an edited one. */
    suspend fun saveTask(task: RoutineTask) = change {
        database.transaction {
            val old = task.id.takeIf { it.isNotBlank() }?.let { taskQueries.byId(it).executeAsOneOrNull() }
            // A new task, or one moved to another group, goes to the end of its group.
            val position = if (old == null || old.group_id != task.groupId) {
                taskQueries.nextPosition(task.groupId).executeAsOne() ?: 0
            } else {
                old.position
            }
            writeTask(task.copy(id = old?.id ?: newId(), title = task.title.trim(), position = position))
        }
    }

    suspend fun deleteTask(taskId: String) = change {
        taskQueries.byId(taskId).executeAsOneOrNull()?.let {
            taskQueries.save(it.id, it.group_id, it.title, it.adjustable, it.position, 1, nowMillis(), 1)
        }
    }

    /** Moves a task one place up ([by] -1) or down ([by] 1) within its group. */
    suspend fun moveTask(task: RoutineTask, siblings: List<RoutineTask>, by: Int) = change {
        database.transaction {
            reorder(siblings.map { it.id to it.position }, task.id, by) { id, position ->
                siblings.first { it.id == id }.let { writeTask(it.copy(position = position)) }
            }
        }
    }

    /** Saves a new group ([RoutineGroup.id] blank) at the end, or an edited one. */
    suspend fun saveGroup(group: RoutineGroup) = change {
        database.transaction {
            val old = group.id.takeIf { it.isNotBlank() }?.let { groupQueries.byId(it).executeAsOneOrNull() }
            val position = old?.position ?: groupQueries.nextPosition().executeAsOne() ?: 0
            writeGroup(group.copy(id = old?.id ?: newId(), name = group.name.trim(), position = position))
        }
    }

    /** Deletes a group and every task in it. */
    suspend fun deleteGroup(group: RoutineGroup, tasks: List<RoutineTask>) = change {
        database.transaction {
            val now = nowMillis()
            groupQueries.save(group.id, group.name, group.position, group.days.toLong(), 1, now, 1)
            tasks.filter { it.groupId == group.id }.forEach {
                taskQueries.save(it.id, it.groupId, it.title, if (it.adjustable) 1 else 0, it.position, 1, now, 1)
            }
        }
    }

    suspend fun moveGroup(group: RoutineGroup, groups: List<RoutineGroup>, by: Int) = change {
        database.transaction {
            reorder(groups.map { it.id to it.position }, group.id, by) { id, position ->
                groups.first { it.id == id }.let { writeGroup(it.copy(position = position)) }
            }
        }
    }

    private fun writeTask(task: RoutineTask) {
        taskQueries.save(task.id, task.groupId, task.title, if (task.adjustable) 1 else 0, task.position, 0, nowMillis(), 1)
    }

    private fun writeGroup(group: RoutineGroup) {
        groupQueries.save(group.id, group.name, group.position, group.days.toLong(), 0, nowMillis(), 1)
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.Default) { block() }

    /** A local edit: saved now, synced soon. */
    private suspend fun change(block: () -> Unit) {
        io(block)
        sync.request()
    }
}

/**
 * Rewrites a routine of blocks as sections ([Sections.convert]), saving only what changed, and
 * returns whether it saved anything.
 *
 * Each record it writes is stamped 1 ms after the records it was made from, not with the current
 * time. That way it replaces the blocks it came from, but loses to any later edit made on another
 * device: an old copy converted before the first sync can't overwrite the routine edited since.
 */
internal fun RoutineDatabase.convertToSections(): Boolean = transactionWithResult {
    val groupTimes = mutableMapOf<String, Long>()
    val oldGroups = routineGroupQueries.active { id, name, position, days, _, updatedAt, _ ->
        groupTimes[id] = updatedAt
        RoutineGroup(id, name, position, days.toInt())
    }.executeAsList()
    if (oldGroups.all(Sections::isSection)) return@transactionWithResult false
    val taskTimes = mutableMapOf<String, Long>()
    val oldTasks = routineTaskQueries.active { id, groupId, title, adjustable, position, _, updatedAt, _ ->
        taskTimes[id] = updatedAt
        RoutineTask(id, groupId, title, adjustable == 1L, position)
    }.executeAsList()
    val (groups, tasks) = Sections.convert(oldGroups, oldTasks)
    val oldGroupOf = oldTasks.associate { it.id to it.groupId }
    val newest = (groupTimes.values + taskTimes.values).maxOrNull() ?: 0

    // A task takes the newer of its own time and its block's; a section, the newest of what made it.
    fun taskTime(id: String) = maxOf(taskTimes[id] ?: 0, groupTimes[oldGroupOf[id]] ?: 0)
    fun sectionTime(group: RoutineGroup): Long {
        val sources = tasks.filter { it.groupId == group.id }.map { taskTime(it.id) } + listOfNotNull(groupTimes[group.id])
        return sources.maxOrNull() ?: newest
    }

    val kept = groups.map { it.id }.toSet()
    oldGroups.filter { it.id !in kept }.forEach {
        routineGroupQueries.save(it.id, it.name, it.position, it.days.toLong(), 1, groupTimes.getValue(it.id) + 1, 1)
    }
    groups.filter { it !in oldGroups }.forEach {
        routineGroupQueries.save(it.id, it.name, it.position, it.days.toLong(), 0, sectionTime(it) + 1, 1)
    }
    tasks.filter { it !in oldTasks }.forEach {
        routineTaskQueries.save(it.id, it.groupId, it.title, if (it.adjustable) 1 else 0, it.position, 0, taskTime(it.id) + 1, 1)
    }
    true
}

/**
 * Moves [id] by [by] places in [items] (id and position, in display order) and numbers them 0, 1,
 * 2, ... again. Only the ones whose position changed are written, so a move touches as few
 * records as it can.
 */
internal fun reorder(items: List<Pair<String, Long>>, id: String, by: Int, write: (id: String, position: Long) -> Unit) {
    val positions = items.toMap()
    reordered(items.map { it.first }, id, by).forEachIndexed { index, each ->
        if (positions[each] != index.toLong()) write(each, index.toLong())
    }
}

internal fun reordered(ids: List<String>, id: String, by: Int): List<String> {
    val from = ids.indexOf(id)
    if (from < 0) return ids
    val to = (from + by).coerceIn(0, ids.lastIndex)
    return ids.toMutableList().apply { add(to, removeAt(from)) }
}

private const val PERIOD = "period"
private const val DAY_MILLIS = 24L * 60 * 60 * 1000