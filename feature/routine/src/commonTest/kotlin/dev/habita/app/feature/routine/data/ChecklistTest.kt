package dev.habita.app.feature.routine.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChecklistTest {
    private val morning = RoutineGroup("morning", "Morning", 0, Days.EVERY_DAY)
    private val office = RoutineGroup("office", "Office", 1, Days.WEEKDAYS)
    private val tasks = listOf(
        RoutineTask("m2", "morning", "Drink water", false, 1),
        RoutineTask("m1", "morning", "Wake up", false, 0),
        RoutineTask("o1", "office", "Commute", true, 0),
    )

    private fun day(weekday: Int) = Day("2026-11-0${weekday + 2}", weekday, "Day", 0)

    @Test
    fun `every section shows every day, weekends included`() {
        val friday = Checklist.of(day(4), listOf(office, morning), tasks, emptySet())
        val saturday = Checklist.of(day(5), listOf(office, morning), tasks, emptySet())

        assertEquals(listOf("morning", "office"), friday.groups.map { it.group.id })
        assertEquals(listOf("morning", "office"), saturday.groups.map { it.group.id })
        assertEquals(3, saturday.total)
    }

    @Test
    fun `tasks keep their order and the next one is the first not done`() {
        val list = Checklist.of(day(0), listOf(morning, office), tasks, setOf("m1"))

        assertEquals(listOf("m1", "m2"), list.groups.first().tasks.map { it.task.id })
        assertEquals(1, list.done)
        assertEquals("m2", list.next?.second?.id)
    }

    @Test
    fun `nothing is next once everything is done`() {
        val list = Checklist.of(day(0), listOf(morning, office), tasks, setOf("m1", "m2", "o1"))

        assertEquals(3, list.done)
        assertNull(list.next)
    }

    @Test
    fun `day labels`() {
        assertEquals("Every day", Days.label(Days.EVERY_DAY))
        assertEquals("Mon–Fri", Days.label(Days.WEEKDAYS))
        assertEquals("Sat, Sun", Days.label(0b1100000))
        assertEquals("Never", Days.label(0))
    }

    @Test
    fun `moving renumbers and writes only what changed`() {
        val written = mutableListOf<Pair<String, Long>>()
        reorder(listOf("a" to 3L, "b" to 4L, "c" to 5L), "c", -1) { id, position -> written += id to position }

        assertEquals(listOf("a" to 0L, "c" to 1L, "b" to 2L), written)

        written.clear()
        reorder(listOf("a" to 0L, "b" to 1L, "c" to 2L), "a", -1) { id, position -> written += id to position }
        assertEquals(emptyList(), written)
    }

    @Test
    fun `the built-in routine is the written one, Fixed and Adjustable in turn`() {
        assertEquals(Defaults.tasks.size, Defaults.tasks.map { it.id }.toSet().size)
        assertEquals(
            listOf("Fixed", "Adjustable", "Fixed", "Adjustable", "Fixed", "Adjustable", "Fixed"),
            Defaults.groups.sortedBy { it.position }.map { it.name },
        )
        assertTrue(Defaults.groups.all { it.days == Days.EVERY_DAY })
        val second = Defaults.tasks.filter { it.groupId == "fixed-2" }.sortedBy { it.position }.map { it.title }
        assertEquals("Drink 4 tsp Bhringrajasava with 4 tsp water (30 mins after breakfast).", second.first())
        assertEquals("Make the office bag ready.", second[1])
    }
}