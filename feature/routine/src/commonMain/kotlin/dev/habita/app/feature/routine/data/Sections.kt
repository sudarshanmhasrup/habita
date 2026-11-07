package dev.habita.app.feature.routine.data

/**
 * The routine is one list of sections, each Fixed or Adjustable, shown every day in order. A
 * section is stored as a group named [FIXED] or [ADJUSTABLE]; its tasks carry the same kind.
 */
internal object Sections {
    const val FIXED = "Fixed"
    const val ADJUSTABLE = "Adjustable"

    fun isSection(group: RoutineGroup): Boolean = group.name == FIXED || group.name == ADJUSTABLE

    fun isAdjustable(group: RoutineGroup): Boolean = group.name == ADJUSTABLE

    /** The built-in after-breakfast tonic, kept in Morning while Office was weekdays only. */
    private const val AFTER_BREAKFAST_TONIC = "morning-31"

    /**
     * The routine as sections, from groups that may still be the old blocks (Morning, Office and
     * so on, each with fixed and adjustable tasks): each block becomes a Fixed section and an
     * Adjustable one, in order, skipping empty ones. The after-breakfast tonic moves to the top of
     * the Fixed section after the first Adjustable one, where the written routine has it. Task
     * ids stay, so what was ticked today stays ticked. The ids it makes are fixed, so every device
     * converting the same routine ends up with the same records.
     */
    fun convert(groups: List<RoutineGroup>, tasks: List<RoutineTask>): Pair<List<RoutineGroup>, List<RoutineTask>> {
        val byGroup = tasks.groupBy { it.groupId }
        val ordered = groups.sortedWith(compareBy({ it.position }, { it.id }))
        val tonic = tasks.firstOrNull { it.id == AFTER_BREAKFAST_TONIC && it.groupId == "morning" && !it.adjustable }
        val sections = mutableListOf<Pair<RoutineGroup, MutableList<RoutineTask>>>()
        var counts = mapOf(FIXED to 0, ADJUSTABLE to 0)

        fun section(kind: String, id: String? = null): MutableList<RoutineTask> {
            val number = counts.getValue(kind) + 1
            counts = counts + (kind to number)
            val group = RoutineGroup(id ?: "${kind.lowercase()}-$number", kind, sections.size.toLong(), Days.EVERY_DAY)
            return mutableListOf<RoutineTask>().also { sections += group to it }
        }

        ordered.forEach { group ->
            val own = byGroup[group.id].orEmpty().sortedWith(compareBy({ it.position }, { it.id }))
            if (isSection(group)) {
                // Already a section: kept as it is.
                sections += group.copy(position = sections.size.toLong()) to own.toMutableList()
                counts = counts + (group.name to counts.getValue(group.name) + 1)
                return@forEach
            }
            val fixed = own.filter { !it.adjustable && it != tonic }
            val adjustable = own.filter { it.adjustable }
            // The tonic leads the Fixed part of the block after Morning.
            val leading = if (tonic != null && group.id != "morning" && sections.none { (_, list) -> tonic in list }) listOf(tonic) else emptyList()
            if (fixed.isNotEmpty() || leading.isNotEmpty()) section(FIXED).addAll(leading + fixed)
            if (adjustable.isNotEmpty()) section(ADJUSTABLE).addAll(adjustable)
        }
        // No later block to take it: it stays at the end.
        if (tonic != null && sections.none { (_, list) -> tonic in list }) section(FIXED).add(tonic)

        val newGroups = sections.map { it.first }
        val newTasks = sections.flatMap { (group, list) ->
            list.mapIndexed { index, task ->
                task.copy(groupId = group.id, position = index.toLong(), adjustable = isAdjustable(group))
            }
        }
        return newGroups to newTasks
    }
}