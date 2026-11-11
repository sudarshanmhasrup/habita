package dev.habita.app.feature.food.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.habita.app.core.Core
import dev.habita.app.core.data.newId
import dev.habita.app.core.data.nowMillis
import dev.habita.app.feature.food.db.FoodDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The plan and the pantry's history, kept on the phone and synced when the server is reachable.
 * Every change records its time, marks the record dirty and asks for a sync.
 */
class FoodRepository(core: Core) {
    private val sync = core.sync
    private val database = FoodDatabase(core.databases.open("food.db", FoodDatabase.Schema))
    private val events = database.foodEventQueries
    private val values = database.foodValueQueries

    init {
        foodSyncTables(database).forEach(sync::register)
    }

    /** The plan as written, or `null` before one is saved. */
    val planText: Flow<String?> = values.byKey(PLAN).asFlow().mapToOneOrNull(Dispatchers.Default)
        .map { it?.value_?.takeIf { text -> text.isNotBlank() } }
        .distinctUntilChanged()

    /** The plan, or `null` with none saved. A saved plan always reads: [savePlan] checks it. */
    val plan: Flow<FoodPlan?> = planText.map { text -> (text?.let(PlanText::parse) as? PlanResult.Ok)?.plan }

    val stockEvents: Flow<List<StockEvent>> = events.active().asFlow().mapToList(Dispatchers.Default).map { rows ->
        rows.mapNotNull { row ->
            val kind = runCatching { StockEvent.Kind.valueOf(row.kind) }.getOrNull() ?: return@mapNotNull null
            StockEvent(row.id, row.item_id, kind, row.quantity, row.day.toInt(), row.at, row.price_paise, row.shop, row.note)
        }
    }

    private val allValues: Flow<Map<String, String>> = values.all().asFlow().mapToList(Dispatchers.Default)
        .map { rows -> rows.associate { it.key to it.value_ } }

    /** The logged days: day → ids of the meals made. See [DayLog]. */
    val made: Flow<Map<Int, Set<String>>> = allValues.map(DayLog::made).distinctUntilChanged()

    /** When each item's current pack was opened and when it's best before: item id → day. */
    val packDates: Flow<PackDates> = allValues.map { PackDates(DayLog.opened(it), DayLog.expires(it)) }.distinctUntilChanged()

    /** The day through which refill questions wait, after "Not now". */
    val snoozedThrough: Flow<Int> = values.byKey(SNOOZE).asFlow().mapToOneOrNull(Dispatchers.Default)
        .map { it?.value_?.toIntOrNull() ?: Int.MIN_VALUE }

    /** Saves a new plan when it reads; otherwise returns what's wrong and keeps the old one. */
    suspend fun savePlan(text: String): PlanResult {
        val result = PlanText.parse(text)
        if (result is PlanResult.Ok) change { values.save(PLAN, text.trim(), nowMillis(), 1) }
        return result
    }

    /** [quantity] of an item is left right now, in its unit. */
    suspend fun count(itemId: String, quantity: Double) = change {
        val now = nowMillis()
        events.save(newId(), itemId, StockEvent.Kind.COUNT.name, quantity, dayOf(now).toLong(), now, null, null, "", 0, now, 1)
    }

    /**
     * Adds purchases to the stock, with their per-pack price and shop. Each entry is an item id
     * and a quantity in its unit.
     */
    suspend fun add(entries: List<Pair<String, Double>>, pricesPaise: Map<String, Long> = emptyMap(), shop: String? = null, note: String = "") =
        change {
            val now = nowMillis()
            database.transaction {
                entries.forEach { (itemId, quantity) ->
                    events.save(
                        newId(), itemId, StockEvent.Kind.ADD.name, quantity, dayOf(now).toLong(), now, pricesPaise[itemId], shop, note, 0, now, 1,
                    )
                }
            }
        }

    /** On [day], exactly the meals in [mealIds] were made; saving a day again replaces it. */
    suspend fun logDay(day: Int, mealIds: Set<String>) = change { values.save(DayLog.madeKey(day), DayLog.encode(mealIds), nowMillis(), 1) }

    /** The item's pack in use was opened on [day]; `null` when it's finished. */
    suspend fun opened(itemId: String, day: Int?) = change { values.save(DayLog.openedKey(itemId), day?.toString().orEmpty(), nowMillis(), 1) }

    /** The item's pack in use is best before [day]; `null` clears it. */
    suspend fun expires(itemId: String, day: Int?) = change { values.save(DayLog.expiresKey(itemId), day?.toString().orEmpty(), nowMillis(), 1) }

    /** The pack in use is finished: its opened day and best-before date are cleared. */
    suspend fun finished(itemId: String) = change {
        val now = nowMillis()
        database.transaction {
            values.save(DayLog.openedKey(itemId), "", now, 1)
            values.save(DayLog.expiresKey(itemId), "", now, 1)
        }
    }

    /** No refill questions through [day]. */
    suspend fun snoozeThrough(day: Int) = change { values.save(SNOOZE, day.toString(), nowMillis(), 1) }

    private suspend fun change(block: () -> Unit) {
        withContext(Dispatchers.Default) { block() }
        sync.request()
    }

    private companion object {
        const val PLAN = "plan"
        const val SNOOZE = "snooze"
    }
}

/** When each item's current pack was opened, and when it's best before: item id → day. */
data class PackDates(val opened: Map<String, Int>, val expires: Map<String, Int>)