package dev.habita.app.feature.routine.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SectionsTest {
    private fun block(id: String, position: Long, days: Int = Days.EVERY_DAY) = RoutineGroup(id, id.replaceFirstChar { it.uppercase() }, position, days)

    private val blocks = listOf(block("morning", 0), block("office", 1, Days.WEEKDAYS), block("evening", 2), block("night", 3))
    private val tasks = listOf(
        RoutineTask("morning-01", "morning", "Wake up.", false, 0),
        RoutineTask("morning-31", "morning", "Drink 4 tsp Bhringrajasava (after breakfast).", false, 1),
        RoutineTask("morning-32", "morning", "Plan the next phase.", false, 2),
        RoutineTask("morning-33", "morning", "Work on the workspace app.", true, 3),
        RoutineTask("office-01", "office", "Make the office bag ready.", false, 0),
        RoutineTask("office-04", "office", "Check AQI.", true, 3),
        RoutineTask("evening-01", "evening", "Arrange bag.", false, 0),
        RoutineTask("evening-12", "evening", "Have dinner.", true, 1),
        RoutineTask("night-01", "night", "Sleep.", false, 0),
    )

    private fun layout(groups: List<RoutineGroup>, tasks: List<RoutineTask>) =
        groups.sortedBy { it.position }.map { group -> group.name to tasks.filter { it.groupId == group.id }.sortedBy { it.position }.map { it.id } }

    @Test
    fun `blocks become the written list of sections`() {
        val (groups, converted) = Sections.convert(blocks, tasks)

        assertEquals(
            listOf(
                "Fixed" to listOf("morning-01", "morning-32"),
                "Adjustable" to listOf("morning-33"),
                "Fixed" to listOf("morning-31", "office-01"),
                "Adjustable" to listOf("office-04"),
                "Fixed" to listOf("evening-01"),
                "Adjustable" to listOf("evening-12"),
                "Fixed" to listOf("night-01"),
            ),
            layout(groups, converted),
        )
        assertTrue(groups.all { it.days == Days.EVERY_DAY })
        assertTrue(converted.filter { it.groupId.startsWith("adjustable") }.all { it.adjustable })
        assertTrue(converted.filter { it.groupId.startsWith("fixed") }.none { it.adjustable })
    }

    @Test
    fun `no task is lost and ids stay, so today's ticks stay`() {
        val (_, converted) = Sections.convert(blocks, tasks)

        assertEquals(tasks.map { it.id }.toSet(), converted.map { it.id }.toSet())
    }

    @Test
    fun `a routine already in sections is left as it is`() {
        val (groups, converted) = Sections.convert(blocks, tasks)

        val again = Sections.convert(groups, converted)

        assertEquals(layout(groups, converted), layout(again.first, again.second))
        assertEquals(converted.toSet(), again.second.toSet())
    }

    @Test
    fun `a block added by hand becomes sections too`() {
        val gym = RoutineGroup("abc", "Weekend", 4, 0b1100000)
        val (groups, converted) = Sections.convert(blocks + gym, tasks + RoutineTask("x", "abc", "Laundry", false, 0))

        assertEquals("Fixed" to listOf("x"), layout(groups, converted).last())
    }
}