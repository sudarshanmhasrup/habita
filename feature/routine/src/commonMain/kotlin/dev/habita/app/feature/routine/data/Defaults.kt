package dev.habita.app.feature.routine.data

/**
 * The routine a new install starts with. Ids are fixed, so every device starts with the same
 * records, and they count as never edited: a change made on any device wins over them. Edit the
 * routine in the app, not here.
 *
 * It is written as the old blocks and turned into sections the same way an existing routine is
 * ([Sections.convert]), so a new install and a converted phone end up with the same records.
 */
internal object Defaults {
    private class Block(val id: String, val name: String, val days: Int, val fixed: List<String>, val adjustable: List<String>)

    private val blocks = listOf(
        Block(
            "morning", "Morning", Days.EVERY_DAY,
            fixed = listOf(
                "Wake up.",
                "Drink water.",
                "Brush your teeth.",
                "Take 1 tsp Rasayan Churna with 1 tsp raw honey.",
                "Clean the bed & room.",
                "Go to GYM.",
                "Come back from GYM.",
                "Turn on the geyser.",
                "Collect & arrange the clothes.",
                "Wash the water bottles.",
                "Get a haircut.",
                "Trim your beard.",
                "Trim your underarms.",
                "Trim your chest hair.",
                "Cut your nails.",
                "Take a bath.",
                "Wash clothes.",
                "Dev Pooja.",
                "Apply sunscreen.",
                "Comb your hair.",
                "Prepare breakfast.",
                "Have your breakfast.",
                "Wash dishes.",
                "Clean the desk.",
                "Arrange the dishes.",
                "Record yesterday's expenses.",
                "Check all email boxes.",
                "Clear email junks.",
                "Clear messages of SMS.",
                "Call Mom & Dad.",
                // Daily, so it stays out of the weekday-only office block.
                "Drink 4 tsp Bhringrajasava with 4 tsp water (30 mins after breakfast).",
                "Plan the next phase.",
            ),
            adjustable = listOf("Work on the workspace app."),
        ),
        Block(
            "office", "Office", Days.WEEKDAYS,
            fixed = listOf(
                "Make the office bag ready.",
                "Clean shoes.",
                "Plan the next phase.",
            ),
            adjustable = listOf(
                "Check AQI.",
                "Commute to the office.",
                "Login & check-in.",
                "Work on the workspace app.",
                "Logout & check-out.",
                "Commute back to the PG.",
            ),
        ),
        Block(
            "evening", "Evening", Days.EVERY_DAY,
            fixed = listOf(
                "Arrange bag.",
                "Wash the towel and micro-fiber cloths.",
                "Clean the desk.",
                "Clean the bed & room.",
                "Wash the tiffin.",
                "Put oats in the fridge.",
                "Call Mom & Dad.",
                "Wash the face.",
                "Change the clothes.",
                "Iron your shirt for tomorrow.",
                "Plan the next phase.",
            ),
            adjustable = listOf("Have dinner."),
        ),
        Block(
            "night", "Night", Days.EVERY_DAY,
            fixed = listOf(
                "Drink 4 tsp Bhringrajasava with 4 tsp water (30 mins after dinner).",
                "Buy bananas for breakfast.",
                "Massage Neelibhringadi oil into scalp for 5 minutes.",
                "Put 2 drops of Anu Tailam into each nostril and inhale.",
                "Plan your next day.",
                "Sleep.",
            ),
            adjustable = emptyList(),
        ),
    )

    private val blockGroups = blocks.mapIndexed { index, block -> RoutineGroup(block.id, block.name, index.toLong(), block.days) }

    private val blockTasks = blocks.flatMap { block ->
        (block.fixed.map { it to false } + block.adjustable.map { it to true }).mapIndexed { index, (title, adjustable) ->
            RoutineTask("${block.id}-${(index + 1).toString().padStart(2, '0')}", block.id, title, adjustable, index.toLong())
        }
    }

    private val sections = Sections.convert(blockGroups, blockTasks)

    /** The sections, Fixed and Adjustable in turn, as the written routine has them. */
    val groups: List<RoutineGroup> = sections.first

    val tasks: List<RoutineTask> = sections.second
}