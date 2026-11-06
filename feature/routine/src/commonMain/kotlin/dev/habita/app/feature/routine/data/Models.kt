package dev.habita.app.feature.routine.data

/**
 * A block of the day, such as Morning. [days] holds the weekdays it shows on as bits, Monday
 * first: bit 0 is Monday, bit 6 Sunday.
 */
data class RoutineGroup(val id: String, val name: String, val position: Long, val days: Int) {
    fun isOn(weekday: Int): Boolean = days and (1 shl weekday) != 0
}

/** One task in a group. An [adjustable] task can move around the day; the rest are fixed. */
data class RoutineTask(
    val id: String,
    val groupId: String,
    val title: String,
    val adjustable: Boolean,
    val position: Long,
)

object Days {
    const val EVERY_DAY = 0b1111111
    const val WEEKDAYS = 0b0011111

    /** Monday to Sunday, for day pickers. */
    val letters = listOf("M", "T", "W", "T", "F", "S", "S")

    private val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** "Every day", "Mon–Fri", "Sat, Sun" or "Never". */
    fun label(days: Int): String = when (days and EVERY_DAY) {
        EVERY_DAY -> "Every day"
        WEEKDAYS -> "Mon–Fri"
        0 -> "Never"
        else -> names.filterIndexed { index, _ -> days and (1 shl index) != 0 }.joinToString(", ")
    }
}

/**
 * One calendar day in the device's time zone. [key] is "2026-11-06", [weekday] runs from 0 for
 * Monday to 6 for Sunday, and [endsAt] is the next midnight, when the checklist resets.
 */
data class Day(val key: String, val weekday: Int, val title: String, val endsAt: Long)

/** Today's checklist: the groups that show today, and the ones that are off, with their tasks. */
data class Checklist(val day: Day, val groups: List<ChecklistGroup>, val off: List<ChecklistGroup>) {
    val total: Int get() = groups.sumOf { it.tasks.size }
    val done: Int get() = groups.sumOf { it.done }

    /** The first task not done yet, top to bottom, and its group. */
    val next: Pair<RoutineGroup, RoutineTask>? get() = groups.firstNotNullOfOrNull { group ->
        group.tasks.firstOrNull { !it.done }?.let { group.group to it.task }
    }

    companion object {
        /** Builds the day from every group and task and the ids of the tasks done today. */
        fun of(day: Day, groups: List<RoutineGroup>, tasks: List<RoutineTask>, done: Set<String>): Checklist {
            val byGroup = tasks.groupBy { it.groupId }
            val (on, off) = groups.sortedWith(compareBy({ it.position }, { it.id })).partition { it.isOn(day.weekday) }
            fun section(group: RoutineGroup): ChecklistGroup {
                val list = byGroup[group.id].orEmpty().sortedWith(compareBy({ it.position }, { it.id }))
                return ChecklistGroup(group, list.map { ChecklistTask(it, it.id in done) })
            }
            return Checklist(day = day, groups = on.map(::section), off = off.map(::section))
        }
    }
}

/** A group's tasks in order; [fixed] and [adjustable] split them the way the routine is read. */
data class ChecklistGroup(val group: RoutineGroup, val tasks: List<ChecklistTask>) {
    val done: Int get() = tasks.count { it.done }
    val fixed: List<ChecklistTask> get() = tasks.filter { !it.task.adjustable }
    val adjustable: List<ChecklistTask> get() = tasks.filter { it.task.adjustable }
}

data class ChecklistTask(val task: RoutineTask, val done: Boolean)