package dev.habita.server

import dev.habita.app.shared.api.ProgramResponse
import dev.habita.app.shared.gym.Equipment
import dev.habita.server.data.Store
import dev.habita.server.data.loadProgram
import dev.habita.server.data.openDatabase
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import kotlin.io.path.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProgramTest {
    @Test
    fun `the real PROGRAM_md is served`() = serverTest { client ->
        val response = client.get("/program") { bearerAuth(client.token()) }.body<ProgramResponse>()
        val program = response.program

        assertEquals(1, response.version)
        assertEquals(listOf("upper-a", "lower-a", "upper-b", "lower-b", "full-body"), program.workouts.map { it.id })
        assertEquals(listOf(1, 2, 3, 4, null), program.workouts.map { it.rotation })
        assertEquals(25, program.exercises.size)
        assertEquals(Equipment.BODYWEIGHT, program.exercise("plank")!!.equipment)
        assertEquals(2500, program.exercise("cable-triceps-pushdown")!!.defaultStep)
        assertEquals(30, program.workout("lower-b")!!.exercises.last().target)
    }

    @Test
    fun `a changed program file becomes a new version`() {
        val (driver, database) = openDatabase(createTempFile("habita", ".db").toString())
        val store = Store(database)
        val file = createTempFile("PROGRAM", ".md")
        val original = Path("program/PROGRAM.md").readText()
        file.writeText(original)

        assertEquals(1, loadProgram(file.toString(), store, NOW).version)
        assertEquals(1, loadProgram(file.toString(), store, NOW + DAY).version)

        val changed = original.replace("| 3    | 30     | 60   |", "| 3    | 40     | 60   |")
        assertTrue(changed != original)
        file.writeText(changed)
        assertEquals(2, loadProgram(file.toString(), store, NOW + 2 * DAY).version)
        assertEquals(2, store.programs().size)
        driver.close()
    }

    @Test
    fun `a missing or broken program file stops the server`() {
        val (driver, database) = openDatabase(createTempFile("habita", ".db").toString())
        val store = Store(database)

        assertFailsWith<IllegalStateException> { loadProgram("missing/PROGRAM.md", store, NOW) }

        val broken = createTempFile("PROGRAM", ".md")
        broken.writeText(Path("program/PROGRAM.md").readText().replace("| 3    | 12     | 90   |", "| 3    | twelve | 90   |"))
        assertFailsWith<IllegalArgumentException> { loadProgram(broken.toString(), store, NOW) }
        driver.close()
    }
}