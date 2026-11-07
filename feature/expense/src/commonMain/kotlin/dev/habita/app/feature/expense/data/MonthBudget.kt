package dev.habita.app.feature.expense.data

import dev.habita.app.shared.api.SyncJson
import kotlinx.serialization.Serializable

/**
 * A month's income and category limits, in paise. Saving the budget stores it for the month being
 * looked at, and it holds for every later month until one has its own, so a raise or a new limit
 * never rewrites the months before it. Months before the first saved budget use the income and
 * limits kept from before budgets were per month. A category missing from [limits] has no limit.
 */
@Serializable
internal data class MonthBudget(val incomePaise: Long, val limits: Map<String, Long>) {
    fun encode(): String = SyncJson.encodeToString(serializer(), this)

    companion object {
        /** Saved as a synced expense value under "budget@2026-11". */
        const val PREFIX = "budget@"

        fun key(month: YearMonth): String =
            PREFIX + month.year.toString().padStart(4, '0') + "-" + month.month.toString().padStart(2, '0')

        fun monthOfKey(key: String): YearMonth? {
            if (!key.startsWith(PREFIX)) return null
            val parts = key.removePrefix(PREFIX).split('-')
            val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val month = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
            return YearMonth(year, month).takeIf { parts.size == 2 }
        }

        fun decode(value: String): MonthBudget? = runCatching { SyncJson.decodeFromString(serializer(), value) }.getOrNull()
    }
}

/** Every month's own budget, by month. */
internal typealias SavedBudgets = Map<YearMonth, MonthBudget>

/**
 * The income and categories [month] runs on: the budget saved for it or the latest month before
 * it, or with none, [baseIncome] and the limits on [categories].
 */
internal fun budgetFor(month: YearMonth, baseIncome: Long, categories: List<Category>, saved: SavedBudgets): Pair<Long, List<Category>> {
    val budget = saved.filterKeys { it <= month }.maxByOrNull { it.key }?.value ?: return baseIncome to categories
    return budget.incomePaise to categories.map { it.copy(limitPaise = budget.limits[it.id] ?: 0) }
}

/** The first month after [month] with a budget of its own, where a change to [month]'s stops. */
internal fun nextOwnBudget(month: YearMonth, saved: SavedBudgets): YearMonth? = saved.keys.filter { it > month }.minOrNull()