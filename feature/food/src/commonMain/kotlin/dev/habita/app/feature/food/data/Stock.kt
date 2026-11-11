package dev.habita.app.feature.food.data

import kotlin.math.floor
import kotlin.math.max

/**
 * Something that happened to an item's stock, in the item's unit. A [COUNT] says how much is
 * left at that moment; an [ADD] is a purchase (with [shop] and [pricePaise], per pack).
 */
data class StockEvent(
    val id: String,
    val itemId: String,
    val kind: Kind,
    val quantity: Double,
    val day: Int,
    val at: Long,
    val pricePaise: Long? = null,
    val shop: String? = null,
    val note: String = "",
) {
    enum class Kind { COUNT, ADD }
}

/**
 * How much of one item is left today. [left] is `null` until the item is first counted or bought.
 * [daysLeft] counts whole days the plan can still eat from it, and [runsOut] is the day it runs
 * out; both `null` when unknown or when no meal uses it. [openedOn] is when the pack in use was
 * opened and [finishBy] the last day it keeps, for items whose plan says how long an open pack
 * lasts.
 */
data class ItemStock(
    val item: FoodItem,
    val dailyUse: Double,
    val left: Double?,
    val daysLeft: Int?,
    val runsOut: Int?,
    val pricePaise: Long?,
    val shop: String?,
    val openedOn: Int? = null,
) {
    val packsLeft: Double? get() = left?.let { it / item.packSize }

    val finishBy: Int? get() = openedOn?.let { day -> item.openDays?.let { day + it } }
}

/**
 * The pantry, worked out from what was bought, counted and made. Only the meals logged as made
 * on a day ([made], day → meal ids) take from the stock: a day that wasn't logged takes nothing,
 * and the app asks about it instead of guessing. Each item starts from its latest count (or,
 * never counted, from its first event) and walks forward: a logged day's meals come off, never
 * below nothing, and a purchase is added after that day's meals. Running out is still forecast
 * from the full plan, counting today's meals as still to come until today is logged.
 *
 * Older apps put a skipped meal's ingredients back as purchases noted "Skipped …"; with only
 * logged meals taken off, those are left out.
 */
object Pantry {
    fun of(
        plan: FoodPlan,
        events: List<StockEvent>,
        today: Int,
        made: Map<Int, Set<String>> = emptyMap(),
        opened: Map<String, Int> = emptyMap(),
    ): List<ItemStock> {
        val byItem = events.filterNot(::isOldSkip).groupBy { it.itemId }
        val mealUse = plan.meals.associate { meal ->
            meal.id to meal.ingredients.filter { it.itemId != null && it.amount != null }
                .groupBy { it.itemId!! }
                .mapValues { (_, list) -> list.sumOf { it.amount!! } }
        }
        val madeDays = made.keys.sorted()
        return plan.items.map { item ->
            fun usedOn(day: Int) = made[day].orEmpty().sumOf { mealUse[it]?.get(item.id) ?: 0.0 }
            val use = plan.dailyUse[item.id] ?: 0.0
            val list = byItem[item.id].orEmpty().sortedWith(compareBy({ it.day }, { it.at }))
            val count = list.lastOrNull { it.kind == StockEvent.Kind.COUNT }
            val since = if (count != null) list.dropWhile { it !== count }.drop(1) else list
            val left = (count ?: since.firstOrNull())?.let { start ->
                var amount = count?.quantity ?: 0.0
                var day = start.day
                fun walkTo(to: Int) {
                    madeDays.filter { it > day && it <= to }.forEach { amount = max(0.0, amount - usedOn(it)) }
                    day = max(day, to)
                }
                since.filter { it.kind == StockEvent.Kind.ADD }.forEach { event ->
                    walkTo(event.day)
                    amount += event.quantity
                }
                walkTo(today)
                amount
            }
            val ahead = left?.let { if (today in made) it else max(0.0, it - use) }
            val daysLeft = if (ahead != null && use > 0) floor(ahead / use).toInt() else null
            val bought = list.lastOrNull { it.kind == StockEvent.Kind.ADD && it.pricePaise != null }
            ItemStock(
                item = item,
                dailyUse = use,
                left = left,
                daysLeft = daysLeft,
                runsOut = daysLeft?.let { today + it },
                pricePaise = bought?.pricePaise ?: item.pricePaise,
                shop = bought?.shop ?: item.shop,
                openedOn = opened[item.id].takeIf { item.openDays != null },
            )
        }
    }

    private fun isOldSkip(event: StockEvent) =
        event.kind == StockEvent.Kind.ADD && event.pricePaise == null && event.note.startsWith("Skipped ")
}