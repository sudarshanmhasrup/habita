package dev.habita.app.feature.food.data

import kotlin.math.ceil
import kotlin.math.max

/** One item to reorder: how many packs, and whether it is [due] (about to run out) or only low. */
data class RefillLine(val stock: ItemStock, val packs: Int, val due: Boolean)

/** This month's food spending against its limit, in paise; [limitPaise] is 0 with no limit. */
data class FoodSpend(val spentPaise: Long, val limitPaise: Long)

/**
 * When to ask about a refill and what to order. Only app-bought items are ordered; market items,
 * such as vegetables, go on the [marketList] instead.
 */
object Refill {
    /** Ask once an app item has this many days or fewer left: a delivery plus a spare day. */
    const val ASK_DAYS = 3

    /** Items this low join a refill too, so one order with one set of fees covers them. */
    const val JOIN_DAYS = 7

    /** A refill buys enough for this many days, so orders come about once a week. */
    const val COVER_DAYS = 10

    /** App items about to run out: what makes Habita ask. */
    fun due(pantry: List<ItemStock>): List<ItemStock> =
        pantry.filter { it.item.buyAt == BuyAt.APP && it.daysLeft != null && it.daysLeft <= ASK_DAYS }

    /** What to order: every app item within [JOIN_DAYS], enough packs to last [COVER_DAYS]. */
    fun lines(pantry: List<ItemStock>): List<RefillLine> = pantry
        .filter { it.item.buyAt == BuyAt.APP && it.daysLeft != null && it.daysLeft <= JOIN_DAYS }
        .sortedBy { it.daysLeft }
        .map { stock ->
            val needed = stock.dailyUse * COVER_DAYS - (stock.left ?: 0.0)
            RefillLine(stock, max(1, ceil(needed / stock.item.packSize).toInt()), due = stock.daysLeft!! <= ASK_DAYS)
        }

    /** The market run for the next week: how much of each market item to buy, in its unit. */
    fun marketList(pantry: List<ItemStock>): List<Pair<FoodItem, Double>> = pantry
        .filter { it.item.buyAt == BuyAt.MARKET && it.dailyUse > 0 }
        .mapNotNull { stock ->
            val needed = ceil(stock.dailyUse * 7 - (stock.left ?: 0.0))
            if (needed > 0) stock.item to needed else null
        }

    /** "Milk and dahi run out Thursday — refill?" for a notification or the home screen. */
    fun question(due: List<ItemStock>, today: Int): String {
        val first = due.minByOrNull { it.runsOut ?: today } ?: return ""
        val names = due.map { shortName(it.item) }
        val list = if (names.size <= 2) names.joinToString(" and ") else "${names.take(2).joinToString(", ")} and ${names.size - 2} more"
        val verb = if (names.size == 1) "runs" else "run"
        return "$list $verb out ${whenLabel(first.runsOut ?: today, today)}. Refill?"
    }

    /** "Amul Taaza Toned Milk 200ml" → "Amul Taaza Toned Milk": names read shorter without pack sizes. */
    fun shortName(item: FoodItem): String =
        item.name.replace(Regex("\\s*\\(?\\d+(\\.\\d+)?\\s*(g|kg|ml|l|pc)\\)?\\b", RegexOption.IGNORE_CASE), "").trim().ifEmpty { item.name }

    /**
     * The request to hand to Claude: compare the delivery apps, fill the cheapest cart and stop at
     * checkout, health first and then budget. It ends with the reply format for logging the order
     * back in Habita ([OrderText]).
     */
    fun request(lines: List<RefillLine>, spend: FoodSpend?, today: Int): String = buildString {
        appendLine("Habita refill request, ${dateLabel(today)}")
        appendLine()
        appendLine("Compare Blinkit, Zepto and Instamart (Amazon too if it's cheaper) for these and fill the cheapest cart.")
        appendLine("Stop at checkout: I'll check it and pay myself.")
        appendLine()
        appendLine("Rules:")
        appendLine("1. Health first: the same product or an equal one. No sweetened, flavoured, masala or lower-protein swaps.")
        appendLine("2. Then budget: the cheapest total including delivery, handling and surge fees. One app beats two when fees make it cheaper.")
        if (spend != null) {
            val left = if (spend.limitPaise > 0) " (${rupees(spend.limitPaise - spend.spentPaise)} left)" else ""
            val limit = if (spend.limitPaise > 0) " of ${rupees(spend.limitPaise)}" else ""
            appendLine("3. Food so far this month: ${rupees(spend.spentPaise)}$limit$left. Tell me if the cart would go over.")
        }
        appendLine()
        appendLine("Items:")
        lines.forEach { line ->
            val stock = line.stock
            val last = stock.pricePaise?.let { price -> " · last ${rupees(price)}" + (stock.shop?.let { " at $it" } ?: "") }.orEmpty()
            val left = stock.runsOut?.let { "runs out ${whenLabel(it, today)}" } ?: "running low"
            appendLine("- ${stock.item.name}: ${line.packs} × ${formatAmount(stock.item.packSize, stock.item.unit)} ($left$last) [${stock.item.id}]")
        }
        appendLine()
        appendLine("Once it's ordered, reply with this so I can log it in Habita. Price is what each line cost in total;")
        appendLine("put any fee on its own line, and anything that isn't food in its own category, such as Household.")
        appendLine()
        appendLine(OrderText.TEMPLATE)
    }.trimEnd()

    /** ₹1,259 or ₹1,259.50. */
    fun rupees(paise: Long): String {
        val rupees = paise / 100
        val grouped = rupees.toString().reversed().chunked(3).joinToString(",").reversed()
        val rest = paise % 100
        return "₹$grouped" + if (rest != 0L) ".${rest.toString().padStart(2, '0')}" else ""
    }
}