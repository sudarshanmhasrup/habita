package dev.habita.server

import dev.habita.server.data.Store
import dev.habita.server.data.loadProgram
import dev.habita.server.data.openDatabase
import kotlin.io.path.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetiredExerciseTest {
    @Test
    fun `an exercise dropped from the file is still served, in no workout`() {
        val (driver, database) = openDatabase(createTempFile("habita", ".db").toString())
        val store = Store(database)
        val file = createTempFile("PROGRAM", ".md")
        val original = Path("program/PROGRAM.md").readText()
        file.writeText(original)
        loadProgram(file.toString(), store, NOW)

        // A new plan that drops the plank: its exercise row and its workout row.
        val withoutPlank = original.lines()
            .filterNot { it.startsWith("| plank ") }
            .joinToString("\n")
        file.writeText(withoutPlank)
        val loaded = loadProgram(file.toString(), store, NOW + DAY)

        assertEquals(2, loaded.version)
        assertEquals("Plank", loaded.program.exercise("plank")?.name)
        assertFalse(loaded.program.inUse("plank"))
        assertTrue(loaded.program.inUse("goblet-squat"))
        driver.close()
    }
}