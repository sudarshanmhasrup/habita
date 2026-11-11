package dev.habita.app.feature.food.data

/**
 * Which meals were made on each day, and when packs were opened, kept as synced food values. A day
 * is one value ("made:20768" → "meal-1,meal-5"), so saving a day again replaces it instead of
 * adding to it. An empty value means nothing was made; no value means the day wasn't logged.
 */
object DayLog {
    private const val MADE = "made:"
    private const val OPENED = "opened:"

    /** How many days back the app asks about days that weren't logged. */
    const val ASK_DAYS = 7

    fun madeKey(day: Int): String = "$MADE$day"

    fun openedKey(itemId: String): String = "$OPENED$itemId"

    fun encode(mealIds: Set<String>): String = mealIds.sorted().joinToString(",")

    /** The logged days from all food values: day → ids of the meals made. */
    fun made(values: Map<String, String>): Map<Int, Set<String>> = values.mapNotNull { (key, value) ->
        val day = key.takeIf { it.startsWith(MADE) }?.removePrefix(MADE)?.toIntOrNull() ?: return@mapNotNull null
        day to value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }.toMap()

    /** When each item's current pack was opened: item id → day. A cleared item is left out. */
    fun opened(values: Map<String, String>): Map<String, Int> = values.mapNotNull { (key, value) ->
        val itemId = key.takeIf { it.startsWith(OPENED) }?.removePrefix(OPENED) ?: return@mapNotNull null
        value.toIntOrNull()?.let { itemId to it }
    }.toMap()

    /**
     * Past days to ask about, oldest first: after the pantry was first counted or bought
     * ([firstDay]), at most [ASK_DAYS] back, before today, and not logged yet.
     */
    fun unlogged(made: Map<Int, Set<String>>, firstDay: Int?, today: Int): List<Int> {
        firstDay ?: return emptyList()
        return (maxOf(firstDay + 1, today - ASK_DAYS) until today).filter { it !in made }
    }
}