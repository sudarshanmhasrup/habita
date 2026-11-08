package dev.habita.app.feature.food.data

import kotlin.math.floor
import kotlin.math.max

/**
 * Something that happened to an item's stock, in the item's unit. A [COUNT] says how much is
 * left at that moment; an [ADD] is a purchase (with [shop] and [pricePaise], per pack) or a
 * skipped meal's ingredients coming back.
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
 * out; both `null` when unknown or when no meal uses it.
 */
data class ItemStock(
    val item: FoodItem,
    val dailyUse: Double,
    val left: Double?,
    val daysLeft: Int?,
    val runsOut: Int?,
    val pricePaise: Long?,
    val shop: String?,
) {
    val packsLeft: Double? get() = left?.let { it / item.packSize }
}

/**
 * The pantry, worked out from the plan and what happened since: the plan eats its daily amount
 * every day, so nothing has to be logged meal by meal. Each item starts from its latest count
 * (or, never counted, from its first purchase), adds what came in after, and takes off a day's use
 * for each day since. A purchase on the day of a count that came before it is added too.
 */
object Pantry {
    fun of(plan: FoodPlan, events: List<StockEvent>, today: Int): List<ItemStock> {
        val byItem = events.groupBy { it.itemId }
        return plan.items.map { item ->
            val use = plan.dailyUse[item.id] ?: 0.0
            val list = byItem[item.id].orEmpty().sortedWith(compareBy({ it.day }, { it.at }))
            val count = list.lastOrNull { it.kind == StockEvent.Kind.COUNT }
            val since = if (count != null) list.dropWhile { it !== count }.drop(1) else list
            val start = count ?: since.firstOrNull()
            val left = start?.let {
                val added = since.filter { it.kind == StockEvent.Kind.ADD }.sumOf { event -> event.quantity }
                val days = max(0, today - it.day)
                max(0.0, (count?.quantity ?: 0.0) + added - use * days)
            }
            val daysLeft = if (left != null && use > 0) floor(left / use).toInt() else null
            val bought = list.lastOrNull { it.kind == StockEvent.Kind.ADD && it.pricePaise != null }
            ItemStock(
                item = item,
                dailyUse = use,
                left = left,
                daysLeft = daysLeft,
                runsOut = daysLeft?.let { today + it },
                pricePaise = bought?.pricePaise ?: item.pricePaise,
                shop = bought?.shop ?: item.shop,
            )
        }
    }
}