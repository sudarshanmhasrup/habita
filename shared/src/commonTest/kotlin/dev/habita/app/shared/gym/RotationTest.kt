package dev.habita.app.shared.gym

import kotlin.test.Test
import kotlin.test.assertEquals

class RotationTest {
    @Test
    fun `starts with the first workout`() {
        assertEquals("a", Rotation.next(testProgram, emptyList()).id)
    }

    @Test
    fun `suggests the workout after the last finished one`() {
        val sessions = listOf(session("s1", "a", 1 * DAY), session("s2", "b", 2 * DAY))

        assertEquals("c", Rotation.next(testProgram, sessions).id)
    }

    @Test
    fun `wraps around after the last workout`() {
        assertEquals("a", Rotation.next(testProgram, listOf(session("s1", "c", 1 * DAY))).id)
    }

    @Test
    fun `fallback workouts do not move the rotation`() {
        val sessions = listOf(session("s1", "a", 1 * DAY), session("s2", "full", 2 * DAY))

        assertEquals("b", Rotation.next(testProgram, sessions).id)
    }

    @Test
    fun `unfinished and deleted sessions do not count`() {
        val sessions = listOf(
            session("s1", "a", 1 * DAY),
            session("s2", "b", 2 * DAY, endedAt = null),
            session("s3", "c", 3 * DAY).copy(deleted = true),
        )

        assertEquals("b", Rotation.next(testProgram, sessions).id)
    }
}