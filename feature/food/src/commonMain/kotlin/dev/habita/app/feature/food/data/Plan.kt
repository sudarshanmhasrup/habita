package dev.habita.app.feature.food.data

import kotlin.math.roundToLong

/** Where an item is bought: from a delivery app, which a refill can order, or at the market. */
enum class BuyAt { APP, MARKET }

/**
 * Something kept in the pantry, such as milk. Amounts are in its [unit]: "g", "ml" or "pc"
 * (pieces). [packSize] is how much one pack holds; [pricePaise] and [shop] are what the plan says
 * it costs and where, until a purchase says otherwise.
 */
data class FoodItem(
    val id: String,
    val name: String,
    val packSize: Double,
    val unit: String,
    val buyAt: BuyAt,
    val shop: String?,
    val pricePaise: Long?,
)

/**
 * One line of a recipe. A tracked line names a pantry item ([itemId]) and takes [amount] of it
 * each time; any other line, such as a pinch of salt, is only shown, as [label] and [amountText].
 */
data class Ingredient(val itemId: String?, val label: String, val amount: Double?, val amountText: String)

/** One meal of the day, with its recipe. [id] is its place in the plan ("meal-1"). */
data class Meal(
    val id: String,
    val name: String,
    val time: String?,
    val proteinGrams: Int?,
    val calories: Int?,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
)

/** The daily food plan: what's kept in the pantry and every meal, eaten every day. */
data class FoodPlan(val items: List<FoodItem>, val meals: List<Meal>) {
    private val byId = items.associateBy { it.id }

    fun item(id: String): FoodItem? = byId[id]

    /** How much of each item the plan eats in a day. */
    val dailyUse: Map<String, Double> = meals.flatMap { it.ingredients }
        .filter { it.itemId != null && it.amount != null }
        .groupBy { it.itemId!! }
        .mapValues { (_, list) -> list.sumOf { it.amount!! } }

    val proteinGrams: Int = meals.sumOf { it.proteinGrams ?: 0 }
    val calories: Int = meals.sumOf { it.calories ?: 0 }
}

/** "80 g", "1.5 kg", "200 ml", "1 L", "3 pc". */
fun formatAmount(amount: Double, unit: String): String = when {
    unit == "g" && amount >= 1000 -> "${trim(amount / 1000)} kg"
    unit == "ml" && amount >= 1000 -> "${trim(amount / 1000)} L"
    else -> "${trim(amount)} $unit"
}

/** 2.0 → "2", 2.5 → "2.5", 2.333 → "2.3". */
fun trim(value: Double): String {
    val tenths = (value * 10).roundToLong()
    return if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${(tenths % 10).let { if (it < 0) -it else it }}"
}

/** The plan read from text, or why it couldn't be. */
sealed interface PlanResult {
    data class Ok(val plan: FoodPlan) : PlanResult

    data class Error(val problems: List<String>) : PlanResult
}

/**
 * The plan as written: markdown that reads well on its own and can be handed to an AI to change.
 * An "Items" section has a table of pantry items; every other "## " section is a meal, with
 * optional "Time:", "Protein:" and "Calories:" lines, a table of ingredients and the steps after
 * "Steps:". An ingredient whose name is an item id is counted against the pantry. See [EXAMPLE].
 */
object PlanText {
    private val units = setOf("g", "ml", "pc")

    fun parse(text: String): PlanResult {
        val problems = mutableListOf<String>()
        val sections = sections(text)
        val itemSection = sections.firstOrNull { it.title.equals("Items", ignoreCase = true) }
        val items = if (itemSection == null) {
            problems += "There's no \"## Items\" section."
            emptyList()
        } else {
            readItems(itemSection, problems)
        }
        val ids = items.associateBy { it.id.lowercase() }
        val meals = sections.filter { it !== itemSection }.mapIndexed { index, section -> readMeal(index, section, ids, problems) }
        if (meals.isEmpty()) problems += "There are no meals: add a \"## \" section for each."
        return if (problems.isEmpty()) PlanResult.Ok(FoodPlan(items, meals)) else PlanResult.Error(problems)
    }

    private class Section(val title: String, val lines: List<String>)

    private fun sections(text: String): List<Section> {
        val result = mutableListOf<Section>()
        var title: String? = null
        val lines = mutableListOf<String>()
        fun close() {
            title?.let { result += Section(it, lines.toList()) }
            lines.clear()
        }
        text.lines().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("## ")) {
                close()
                title = line.removePrefix("## ").trim()
            } else if (title != null) {
                lines += line
            }
        }
        close()
        return result
    }

    /** The rows of the first table in [lines], as cells, header first, without the --- row. */
    private fun table(lines: List<String>): List<List<String>> = lines
        .dropWhile { !it.startsWith("|") }
        .takeWhile { it.startsWith("|") }
        .map { row -> row.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() } }
        .filterNot { cells -> cells.all { cell -> cell.isNotEmpty() && cell.all { it == '-' || it == ':' } } }

    private fun column(header: List<String>, vararg names: String): Int =
        header.indexOfFirst { cell -> names.any { cell.equals(it, ignoreCase = true) } }

    private fun readItems(section: Section, problems: MutableList<String>): List<FoodItem> {
        val rows = table(section.lines)
        if (rows.size < 2) {
            problems += "Items: add a table with Id, Item, Pack and Unit columns."
            return emptyList()
        }
        val header = rows.first()
        val id = column(header, "id")
        val name = column(header, "item", "name")
        val pack = column(header, "pack")
        val unit = column(header, "unit")
        val buy = column(header, "buy")
        val shop = column(header, "shop")
        val price = column(header, "price")
        if (listOf(id, name, pack, unit).any { it < 0 }) {
            problems += "Items: the table needs Id, Item, Pack and Unit columns."
            return emptyList()
        }
        val seen = mutableSetOf<String>()
        return rows.drop(1).mapNotNull { cells ->
            fun cell(index: Int) = cells.getOrNull(index)?.takeIf { index >= 0 }.orEmpty()
            val itemId = cell(id)
            val label = "Items, \"$itemId\""
            when {
                itemId.isEmpty() || itemId.any { it.isWhitespace() } -> problems += "Items: every item needs a one-word id."
                !seen.add(itemId.lowercase()) -> problems += "$label is listed twice."
                number(cell(pack))?.takeIf { it > 0 } == null -> problems += "$label: the pack size should be a number."
                cell(unit).lowercase() !in units -> problems += "$label: the unit should be g, ml or pc."
                else -> return@mapNotNull FoodItem(
                    id = itemId,
                    name = cell(name).ifEmpty { itemId },
                    packSize = number(cell(pack))!!,
                    unit = cell(unit).lowercase(),
                    buyAt = if (cell(buy).equals("market", ignoreCase = true)) BuyAt.MARKET else BuyAt.APP,
                    shop = cell(shop).ifEmpty { null },
                    pricePaise = number(cell(price))?.let { (it * 100).roundToLong() },
                )
            }
            null
        }
    }

    private fun readMeal(index: Int, section: Section, items: Map<String, FoodItem>, problems: MutableList<String>): Meal {
        fun value(key: String) = section.lines.firstOrNull { it.startsWith("$key:", ignoreCase = true) }?.substringAfter(':')?.trim()
        val rows = table(section.lines)
        val ingredients = rows.drop(1).mapNotNull { cells ->
            val name = cells.getOrNull(0).orEmpty()
            val amountText = cells.getOrNull(1).orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val item = items[name.lowercase()]
            if (item == null) return@mapNotNull Ingredient(null, name, null, amountText)
            val amount = number(amountText)
            if (amount == null || amount <= 0) {
                problems += "${section.title}: \"$name\" needs an amount in ${item.unit}, such as 80."
                null
            } else {
                Ingredient(item.id, item.name, amount, formatAmount(amount, item.unit))
            }
        }
        if (rows.size < 2) problems += "${section.title}: add a table of ingredients (Item, Amount)."
        val stepsAt = section.lines.indexOfFirst { it.equals("Steps:", ignoreCase = true) }
        val steps = if (stepsAt < 0) {
            emptyList()
        } else {
            section.lines.drop(stepsAt + 1)
                .filter { it.isNotEmpty() && !it.startsWith("|") }
                .map { it.replace(Regex("^(\\d+[.)]|[-*])\\s*"), "") }
        }
        return Meal(
            id = "meal-${index + 1}",
            name = section.title,
            time = value("Time"),
            proteinGrams = value("Protein")?.let(::number)?.toInt(),
            calories = value("Calories")?.let(::number)?.toInt(),
            ingredients = ingredients,
            steps = steps,
        )
    }

    /** The first number in [text]: "~22g" is 22, "1,259" is 1259. */
    internal fun number(text: String): Double? =
        Regex("\\d+(\\.\\d+)?").find(text.replace(",", ""))?.value?.toDoubleOrNull()

    /** A small plan to start from, showing the format. */
    val EXAMPLE: String = """
        # Food plan

        ## Items

        | Id | Item | Pack | Unit | Buy | Shop | Price |
        | --- | --- | --- | --- | --- | --- | --- |
        | oats | Rolled oats 1 kg | 1000 | g | app | Blinkit | 166 |
        | milk | Toned milk 200 ml (tetra pack) | 200 | ml | app | Zepto | 17 |
        | banana | Banana | 1 | pc | market | | 5 |

        ## Breakfast - Overnight oats

        Time: 08:00
        Protein: 15
        Calories: 500

        | Item | Amount |
        | --- | --- |
        | oats | 80 |
        | milk | 200 |
        | banana | 1 |

        Steps:

        1. Mix the oats and milk in a lidded bowl and keep it in the fridge overnight.
        2. Slice the banana on top in the morning.
    """.trimIndent()
}