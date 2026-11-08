package dev.habita.app.feature.expense.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A paid order to log split by category, such as a grocery delivery with a kettle in it.
 * [bankSms] is whether the bank sends an SMS for it (UPI, card); a wallet or cash payment has
 * none, so it is saved at once, [cash] telling the two apart. The [parts] add up to the amount.
 */
@Serializable
data class PaidOrder(
    val description: String,
    val amountPaise: Long,
    val at: Long,
    val parts: List<ExpensePart>,
    val bankSms: Boolean = true,
    val cash: Boolean = false,
)

/** What logging an order did. */
sealed interface OrderOutcome {
    /** Its bank payment was already in and is split now. */
    data class Matched(val expense: Expense) : OrderOutcome

    /** Saved as a payment now (wallet or cash). */
    data class Added(val expense: Expense) : OrderOutcome

    /** Waiting for its bank SMS, which will be split when it comes. */
    data object Waiting : OrderOutcome
}

/** How pending orders and parts are stored: unknown fields from a newer app are kept out. */
internal val PendingJson = Json { ignoreUnknownKeys = true }

private val PartsSerializer = ListSerializer(ExpensePart.serializer())

internal fun encodeParts(parts: List<ExpensePart>): String? =
    if (parts.isEmpty()) null else PendingJson.encodeToString(PartsSerializer, parts)

internal fun decodeParts(text: String?): List<ExpensePart> =
    text?.let { runCatching { PendingJson.decodeFromString(PartsSerializer, it) }.getOrNull() }.orEmpty()

/** The category of the biggest part, which screens showing one category use. */
internal fun mainCategory(parts: List<ExpensePart>): String? =
    parts.groupBy { it.categoryId }.maxByOrNull { (_, list) -> list.sumOf { it.amountPaise } }?.key

/** This payment as [order]: its description and its split. */
internal fun Expense.withOrder(order: PaidOrder): Expense =
    copy(description = order.description, parts = order.parts, categoryId = mainCategory(order.parts))