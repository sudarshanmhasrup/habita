package dev.habita.app.feature.food.data

import kotlin.math.roundToLong

/** How an order was paid: by bank (UPI or card, so a bank SMS comes), from an app wallet, or in cash. */
enum class OrderPayment { BANK, WALLET, CASH }

/**
 * One line of an order. A line for a pantry item ([itemId]) adds [quantity] packs to its stock;
 * any other line, such as a kettle or a fee, is only money. [pricePaise] is the line's total.
 */
data class OrderLine(val itemId: String?, val name: String, val quantity: Double, val pricePaise: Long, val category: String)

/** An order to log: where from, how it was paid, and its lines. */
data class Order(val shop: String, val payment: OrderPayment, val lines: List<OrderLine>) {
    val totalPaise: Long get() = lines.sumOf { it.pricePaise }
}

/** The order read from text, or why it couldn't be. */
sealed interface OrderResult {
    data class Ok(val order: Order) : OrderResult

    data class Error(val problems: List<String>) : OrderResult
}

/**
 * An order as Claude writes it back after filling a cart ([Refill.request]): "Shop:", "Paid:",
 * an optional "Total:" that the lines must add up to, and a table of Item, Qty, Price and
 * Category. Item is a pantry item's id or any name; Category defaults to Food.
 */
object OrderText {
    val TEMPLATE: String = """
        # Habita order

        Shop: Zepto
        Paid: UPI
        Total: 806

        | Item | Qty | Price | Category |
        | --- | --- | --- | --- |
        | milk | 21 | 357 | Food |
        | Kettle 1.6 L | 1 | 449 | Household |
    """.trimIndent()

    fun parse(text: String, plan: FoodPlan?): OrderResult {
        val problems = mutableListOf<String>()
        val lines = text.lines().map { it.trim() }
        fun value(key: String) = lines.firstOrNull { it.startsWith("$key:", ignoreCase = true) }?.substringAfter(':')?.trim()
        val shop = value("Shop")?.takeIf { it.isNotEmpty() }
        if (shop == null) problems += "Add a \"Shop:\" line, such as Shop: Zepto."
        val payment = when (value("Paid")?.lowercase()) {
            null, "", "upi", "bank", "card", "debit card", "credit card", "netbanking" -> OrderPayment.BANK
            "wallet" -> OrderPayment.WALLET
            "cash", "cod", "cash on delivery" -> OrderPayment.CASH
            else -> if (value("Paid")!!.contains("wallet", ignoreCase = true)) OrderPayment.WALLET else OrderPayment.BANK
        }
        val rows = lines.dropWhile { !it.startsWith("|") }
            .takeWhile { it.startsWith("|") }
            .map { row -> row.removePrefix("|").removeSuffix("|").split("|").map { it.trim() } }
            .filterNot { cells -> cells.all { cell -> cell.isNotEmpty() && cell.all { it == '-' || it == ':' } } }
        val header = rows.firstOrNull().orEmpty()
        fun column(name: String) = header.indexOfFirst { it.equals(name, ignoreCase = true) }
        val item = column("Item")
        val qty = column("Qty").takeIf { it >= 0 } ?: column("Quantity")
        val price = column("Price")
        val category = column("Category")
        if (item < 0 || qty < 0 || price < 0) {
            problems += "Add a table with Item, Qty and Price columns (and Category, Food by default)."
            return OrderResult.Error(problems)
        }
        val orderLines = rows.drop(1).mapNotNull { cells ->
            val name = cells.getOrNull(item).orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val quantity = PlanText.number(cells.getOrNull(qty).orEmpty())
            val rupees = PlanText.number(cells.getOrNull(price).orEmpty())
            if (quantity == null || rupees == null) {
                problems += "\"$name\": Qty and Price should be numbers."
                return@mapNotNull null
            }
            val known = plan?.items?.firstOrNull { it.id.equals(name, ignoreCase = true) }
            OrderLine(
                itemId = known?.id,
                name = known?.name ?: name,
                quantity = quantity,
                pricePaise = (rupees * 100).roundToLong(),
                category = cells.getOrNull(category)?.takeIf { category >= 0 && it.isNotEmpty() } ?: "Food",
            )
        }
        if (orderLines.isEmpty()) problems += "The table has no lines."
        val order = Order(shop.orEmpty(), payment, orderLines)
        value("Total")?.let(PlanText::number)?.let { total ->
            val totalPaise = (total * 100).roundToLong()
            if (orderLines.isNotEmpty() && totalPaise != order.totalPaise) {
                problems += "The lines add up to ${Refill.rupees(order.totalPaise)}, not ${Refill.rupees(totalPaise)}. Add the difference as a line, such as a fee."
            }
        }
        return if (problems.isEmpty()) OrderResult.Ok(order) else OrderResult.Error(problems)
    }
}