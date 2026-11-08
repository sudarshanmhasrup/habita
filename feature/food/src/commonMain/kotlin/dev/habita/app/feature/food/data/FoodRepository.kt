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
     * Adds to the stock: purchases, with their per-pack price and shop, or a skipped meal's
     * ingredients. Each entry is an item id and a quantity in its unit.
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