package dev.habita.app.shared.gym

import kotlin.math.roundToInt

class ProgramFormatException(val line: Int, message: String) :
    IllegalArgumentException("PROGRAM.md line $line: $message")

/**
 * Parses `PROGRAM.md`. The format is strict on purpose: anything it does not understand inside the
 * Exercises, Warm-ups and Workouts sections is an error, so a typo never turns into wrong data.
 * Other sections and prose are ignored.
 */
object ProgramParser {
    private val exerciseHeader = listOf("id", "name", "equipment", "measure", "per side", "step")
    private val warmUpHeader = listOf("name", "amount")
    private val workoutHeader = listOf("exercise", "sets", "target", "rest")
    private val idPattern = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
    private val separatorPattern = Regex("^\\|[\\s:|-]+\\|$")

    private enum class Section { OTHER, EXERCISES, WARM_UPS, WORKOUTS }

    private class Block(val name: String, val line: Int, val section: Section) {
        val keys = mutableMapOf<String, Pair<String, Int>>()
        var rows: List<Row>? = null
    }

    private class Row(val cells: List<String>, val line: Int)

    fun parse(markdown: String): Program {
        val lines = markdown.lines()
        val exercises = mutableListOf<Exercise>()
        val warmUps = mutableListOf<WarmUp>()
        val workouts = mutableListOf<Workout>()
        var section = Section.OTHER
        var block: Block? = null

        fun finishBlock() {
            val finished = block ?: return
            when (finished.section) {
                Section.WARM_UPS -> warmUps += warmUp(finished)
                Section.WORKOUTS -> workouts += workout(finished)
                else -> Unit
            }
            block = null
        }

        var index = 0
        while (index < lines.size) {
            val lineNumber = index + 1
            val line = lines[index].trim()
            when {
                line.startsWith("## ") -> {
                    finishBlock()
                    section = when (line.removePrefix("## ").trim().lowercase()) {
                        "exercises" -> Section.EXERCISES
                        "warm-ups" -> Section.WARM_UPS
                        "workouts" -> Section.WORKOUTS
                        else -> Section.OTHER
                    }
                    index++
                }

                line.startsWith("### ") && section in setOf(Section.WARM_UPS, Section.WORKOUTS) -> {
                    finishBlock()
                    block = Block(line.removePrefix("### ").trim(), lineNumber, section)
                    index++
                }

                line.startsWith("|") -> {
                    val end = (index until lines.size)
                        .firstOrNull { !lines[it].trim().startsWith("|") } ?: lines.size
                    val tableLines = (index until end).map { lines[it].trim() to it + 1 }
                    when (section) {
                        Section.EXERCISES -> {
                            readTable(tableLines, exerciseHeader).mapTo(exercises, ::exercise)
                        }

                        Section.WARM_UPS, Section.WORKOUTS -> {
                            val current = block
                                ?: throw ProgramFormatException(lineNumber, "Table outside a ### block")
                            if (current.rows != null) {
                                throw ProgramFormatException(lineNumber, "Second table in \"${current.name}\"")
                            }
                            val header = if (section == Section.WARM_UPS) warmUpHeader else workoutHeader
                            current.rows = readTable(tableLines, header)
                        }

                        Section.OTHER -> Unit
                    }
                    index = end
                }

                line.startsWith("- ") && block != null -> {
                    val entry = line.removePrefix("- ")
                    val separator = entry.indexOf(':')
                    if (separator < 0) throw ProgramFormatException(lineNumber, "Expected \"- Key: value\"")
                    val key = entry.substring(0, separator).trim().lowercase()
                    val value = entry.substring(separator + 1).trim()
                    val current = block!!
                    if (key in current.keys) throw ProgramFormatException(lineNumber, "Duplicate key \"$key\"")
                    current.keys[key] = value to lineNumber
                    index++
                }

                else -> index++
            }
        }
        finishBlock()

        return Program(exercises, warmUps, workouts).also(::validate)
    }

    private fun readTable(lines: List<Pair<String, Int>>, expectedHeader: List<String>): List<Row> {
        val (headerLine, headerNumber) = lines.first()
        val header = cells(headerLine).map { it.lowercase() }
        if (header != expectedHeader) {
            throw ProgramFormatException(
                headerNumber,
                "Expected columns ${expectedHeader.joinToString(" | ")}, found ${header.joinToString(" | ")}",
            )
        }
        val separator = lines.getOrNull(1)
        if (separator == null || !separatorPattern.matches(separator.first)) {
            throw ProgramFormatException(headerNumber + 1, "Expected a table separator row")
        }
        return lines.drop(2).map { (text, number) ->
            val row = cells(text)
            if (row.size != expectedHeader.size) {
                throw ProgramFormatException(number, "Expected ${expectedHeader.size} cells, found ${row.size}")
            }
            Row(row, number)
        }
    }

    private fun cells(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

    private fun exercise(row: Row): Exercise {
        val (id, name, equipment, measure, perSide, step) = row.cells
        val parsedEquipment = enumValue<Equipment>(equipment, row.line, "equipment")
        return Exercise(
            id = id(id, row.line),
            name = name.ifEmpty { throw ProgramFormatException(row.line, "Exercise name is empty") },
            equipment = parsedEquipment,
            measure = enumValue<Measure>(measure, row.line, "measure"),
            perSide = when (perSide.lowercase()) {
                "yes" -> true
                "no" -> false
                else -> throw ProgramFormatException(row.line, "Per side must be yes or no, found \"$perSide\"")
            },
            defaultStep = if (parsedEquipment == Equipment.BODYWEIGHT) {
                positiveInt(step, row.line, "step")
            } else {
                kilogramsToGrams(step, row.line)
            },
        )
    }

    private fun warmUp(block: Block): WarmUp {
        val rows = block.rows ?: throw ProgramFormatException(block.line, "Warm-up \"${block.name}\" has no table")
        return WarmUp(
            id = id(requiredKey(block, "id"), block.keys.getValue("id").second),
            name = block.name,
            items = rows.map { WarmUpItem(name = it.cells[0], amount = it.cells[1]) },
            restSeconds = positiveInt(requiredKey(block, "rest"), block.keys.getValue("rest").second, "rest"),
        ).also { rejectUnknownKeys(block, setOf("id", "rest")) }
    }

    private fun workout(block: Block): Workout {
        val rows = block.rows ?: throw ProgramFormatException(block.line, "Workout \"${block.name}\" has no table")
        val rotation = requiredKey(block, "rotation")
        return Workout(
            id = id(requiredKey(block, "id"), block.keys.getValue("id").second),
            name = block.name,
            warmUpId = requiredKey(block, "warm-up"),
            rotation = if (rotation.lowercase() == "fallback") {
                null
            } else {
                positiveInt(rotation, block.keys.getValue("rotation").second, "rotation")
            },
            exercises = rows.map { row ->
                WorkoutExercise(
                    exerciseId = row.cells[0],
                    sets = positiveInt(row.cells[1], row.line, "sets"),
                    target = positiveInt(row.cells[2], row.line, "target"),
                    restSeconds = positiveInt(row.cells[3], row.line, "rest"),
                )
            },
        ).also { rejectUnknownKeys(block, setOf("id", "warm-up", "rotation")) }
    }

    private fun validate(program: Program) {
        if (program.exercises.isEmpty()) throw ProgramFormatException(1, "No exercises found")
        if (program.workouts.none { it.rotation != null }) {
            throw ProgramFormatException(1, "No workout has a rotation position")
        }
        duplicate(program.exercises.map { it.id })?.let {
            throw ProgramFormatException(1, "Duplicate exercise id \"$it\"")
        }
        duplicate(program.warmUps.map { it.id })?.let {
            throw ProgramFormatException(1, "Duplicate warm-up id \"$it\"")
        }
        duplicate(program.workouts.map { it.id })?.let {
            throw ProgramFormatException(1, "Duplicate workout id \"$it\"")
        }
        duplicate(program.workouts.mapNotNull { it.rotation })?.let {
            throw ProgramFormatException(1, "Duplicate rotation position $it")
        }
        val exerciseIds = program.exercises.map { it.id }.toSet()
        val warmUpIds = program.warmUps.map { it.id }.toSet()
        program.workouts.forEach { workout ->
            if (workout.warmUpId !in warmUpIds) {
                throw ProgramFormatException(1, "Workout \"${workout.name}\" uses unknown warm-up \"${workout.warmUpId}\"")
            }
            workout.exercises.forEach {
                if (it.exerciseId !in exerciseIds) {
                    throw ProgramFormatException(1, "Workout \"${workout.name}\" uses unknown exercise \"${it.exerciseId}\"")
                }
            }
        }
    }

    private fun <T> duplicate(values: List<T>): T? =
        values.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.key

    private fun requiredKey(block: Block, key: String): String =
        block.keys[key]?.first ?: throw ProgramFormatException(block.line, "\"${block.name}\" is missing \"- $key:\"")

    private fun rejectUnknownKeys(block: Block, allowed: Set<String>) {
        block.keys.entries.firstOrNull { it.key !in allowed }?.let {
            throw ProgramFormatException(it.value.second, "Unknown key \"${it.key}\"")
        }
    }

    private fun id(value: String, line: Int): String {
        if (!idPattern.matches(value)) {
            throw ProgramFormatException(line, "Id \"$value\" must be lowercase kebab-case")
        }
        return value
    }

    private fun positiveInt(value: String, line: Int, name: String): Int =
        value.toIntOrNull()?.takeIf { it > 0 }
            ?: throw ProgramFormatException(line, "$name must be a positive whole number, found \"$value\"")

    private fun kilogramsToGrams(value: String, line: Int): Int =
        value.toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 1000).roundToInt() }
            ?: throw ProgramFormatException(line, "step must be a positive number of kg, found \"$value\"")

    private inline fun <reified T : Enum<T>> enumValue(value: String, line: Int, name: String): T =
        enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: throw ProgramFormatException(
                line,
                "$name must be one of ${enumValues<T>().joinToString { it.name.lowercase() }}, found \"$value\"",
            )

    private operator fun <T> List<T>.component6(): T = this[5]
}