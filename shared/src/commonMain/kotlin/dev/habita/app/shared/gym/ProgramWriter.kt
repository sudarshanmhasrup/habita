package dev.habita.app.shared.gym

/**
 * Writes a [Program] back as the tables of `PROGRAM.md`, in the format [ProgramParser] reads, so a
 * plan can be handed to someone (or an AI) to edit and pasted back. Prose sections are not kept.
 */
object ProgramWriter {
    fun write(program: Program): String = buildString {
        append("## Exercises\n\n")
        append(
            table(
                listOf("Id", "Name", "Equipment", "Measure", "Per side", "Step"),
                program.exercises.map {
                    listOf(
                        it.id,
                        it.name,
                        it.equipment.name.lowercase(),
                        it.measure.name.lowercase(),
                        if (it.perSide) "yes" else "no",
                        if (it.isWeighted) kilograms(it.defaultStep) else it.defaultStep.toString(),
                    )
                },
            ),
        )
        val withAlternatives = program.exercises.filter { it.alternatives.isNotEmpty() }
        if (withAlternatives.isNotEmpty()) {
            append("\n\n## Alternatives\n\n")
            append(table(listOf("Exercise", "Alternatives"), withAlternatives.map { listOf(it.id, it.alternatives.joinToString(", ")) }))
        }
        append("\n\n## Warm-ups")
        program.warmUps.forEach { warmUp ->
            append("\n\n### ${warmUp.name}\n\n- Id: ${warmUp.id}\n- Rest: ${warmUp.restSeconds}\n\n")
            append(table(listOf("Name", "Amount"), warmUp.items.map { listOf(it.name, it.amount) }))
        }
        append("\n\n## Workouts")
        program.workouts.forEach { workout ->
            append("\n\n### ${workout.name}\n\n- Id: ${workout.id}\n- Warm-up: ${workout.warmUpId}\n")
            append("- Rotation: ${workout.rotation ?: "fallback"}\n\n")
            append(
                table(
                    listOf("Exercise", "Sets", "Target", "Rest"),
                    workout.exercises.map { listOf(it.exerciseId, "${it.sets}", "${it.target}", "${it.restSeconds}") },
                ),
            )
        }
    }

    /** 2500 → "2.5", 5000 → "5". */
    fun kilograms(grams: Int): String {
        val fraction = (grams % 1000).toString().padStart(3, '0').trimEnd('0')
        return if (fraction.isEmpty()) "${grams / 1000}" else "${grams / 1000}.$fraction"
    }

    private fun table(header: List<String>, rows: List<List<String>>): String {
        val widths = header.indices.map { column -> (rows.map { it[column] } + header[column]).maxOf { it.length } }
        fun line(cells: List<String>) = cells.mapIndexed { i, cell -> cell.padEnd(widths[i]) }.joinToString(" | ", "| ", " |")
        return (listOf(line(header), widths.joinToString("|", "|", "|") { "-".repeat(it + 2) }) + rows.map(::line))
            .joinToString("\n")
    }
}