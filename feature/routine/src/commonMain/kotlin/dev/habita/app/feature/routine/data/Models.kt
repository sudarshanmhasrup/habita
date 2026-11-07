package dev.habita.app.feature.routine.data

/**
 * A section of the routine, named Fixed or Adjustable ([Sections]), shown every day. [days] is
 * kept from when blocks could be weekday-only, and is always every day now.
 */
data class RoutineGroup(val id: String, val name: String, val position: Long, val days: Int)

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

/** Today's checklist: every section in order, with its tasks and which are done. */
data class Checklist(val day: Day, val groups: List<ChecklistGroup>) {
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
            val sections = groups.sortedWith(compareBy({ it.position }, { it.id })).map { group ->
                val list = byGroup[group.id].orEmpty().sortedWith(compareBy({ it.position }, { it.id }))
                ChecklistGroup(group, list.map { ChecklistTask(it, it.id in done) })
            }
            return Checklist(day = day, groups = sections)
        }
    }
}

/** A section's tasks in order. */
data class ChecklistGroup(val group: RoutineGroup, val tasks: List<ChecklistTask>) {
    val done: Int get() = tasks.count { it.done }
}

data class ChecklistTask(val task: RoutineTask, val done: Boolean)