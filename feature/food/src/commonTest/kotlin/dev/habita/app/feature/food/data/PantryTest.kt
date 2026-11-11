package dev.habita.app.feature.food.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PantryTest {
    private val milk = FoodItem("milk", "Milk 200 ml", 200.0, "ml", BuyAt.APP, "Zepto", 17_00)
    private val oats = FoodItem("oats", "Oats 1 kg", 1000.0, "g", BuyAt.APP, "Blinkit", 166_00)
    private val banana = FoodItem("banana", "Banana", 1.0, "pc", BuyAt.MARKET, null, 5_00)
    private val plan = FoodPlan(
        items = listOf(milk, oats, banana),
        meals = listOf(
            Meal("meal-1", "Breakfast", null, 20, 600, listOf(Ingredient("milk", "Milk", 400.0, ""), Ingredient("oats", "Oats", 80.0, ""), Ingredient("banana", "Banana", 2.0, "")), emptyList()),
        ),
    )
    private val day = 20_000

    private fun buy(item: String, quantity: Double, day: Int, at: Long = day * 10L, price: Long? = null) =
        StockEvent("$item-$at", item, StockEvent.Kind.ADD, quantity, day, at, price, "Zepto")

    private fun count(item: String, quantity: Double, day: Int, at: Long = day * 10L) =
        StockEvent("$item-c-$at", item, StockEvent.Kind.COUNT, quantity, day, at)

    /** Every meal made on each day from [from] through [to]. */
    private fun madeEvery(from: Int, to: Int) = (from..to).associateWith { setOf("meal-1") }

    @Test
    fun `stock goes down by the meals made from the first purchase`() {
        val pantry = Pantry.of(plan, listOf(buy("milk", 4200.0, day, price = 17_00)), today = day + 3, made = madeEvery(day, day + 3))
        val stock = pantry.first { it.item.id == "milk" }

        assertEquals(3000.0, stock.left)
        assertEquals(15.0, stock.packsLeft)
        assertEquals(7, stock.daysLeft)
        assertEquals(day + 10, stock.runsOut)
        assertNull(pantry.first { it.item.id == "oats" }.left)
    }

    @Test
    fun `a count resets the stock, and later purchases add to it`() {
        val events = listOf(
            buy("oats", 1000.0, day),
            count("oats", 500.0, day + 2, at = 1),
            buy("oats", 1000.0, day + 2, at = 2),
            buy("oats", 2000.0, day + 1),
        )
        val stock = Pantry.of(plan, events, today = day + 4, made = madeEvery(day, day + 4)).first { it.item.id == "oats" }

        assertEquals(500.0 + 1000.0 - 80.0 * 2, stock.left)
    }

    @Test
    fun `stock never goes below nothing`() {
        val stock = Pantry.of(plan, listOf(buy("milk", 400.0, day)), today = day + 10, made = madeEvery(day, day + 10)).first { it.item.id == "milk" }
        assertEquals(0.0, stock.left)
        assertEquals(0, stock.daysLeft)
    }

    @Test
    fun `refills ask for app items about to run out and join the ones running low`() {
        val events = listOf(buy("milk", 800.0, day), buy("oats", 480.0, day), buy("banana", 2.0, day))
        val pantry = Pantry.of(plan, events, today = day, made = madeEvery(day, day))
        val due = Refill.due(pantry)

        assertEquals(listOf("milk"), due.map { it.item.id })
        val lines = Refill.lines(pantry)
        assertEquals(listOf("milk", "oats"), lines.map { it.stock.item.id })
        // Ten days of milk is 4,000 ml; 800 are left, so 16 packs of 200.
        assertEquals(16, lines[0].packs)
        assertTrue(lines[0].due)
        assertEquals(1, lines[1].packs)
        assertEquals(listOf(banana to 12.0), Refill.marketList(pantry))
    }

    @Test
    fun `the question names what runs out and when`() {
        val pantry = Pantry.of(plan, listOf(buy("milk", 800.0, day), buy("oats", 80.0, day)), today = day, made = madeEvery(day, day))
        assertEquals("Milk and Oats run out tomorrow. Refill?", Refill.question(Refill.due(pantry), day))
    }

    @Test
    fun `the request carries the rules, the budget and the reply format`() {
        val pantry = Pantry.of(plan, listOf(buy("milk", 800.0, day, price = 17_00)), today = day, made = madeEvery(day, day))
        val text = Refill.request(Refill.lines(pantry), FoodSpend(3_900_00, 5_000_00), day)

        assertTrue("Health first" in text, text)
        assertTrue("₹3,900 of ₹5,000 (₹1,100 left)" in text, text)
        assertTrue("Milk 200 ml: 16 × 200 ml" in text, text)
        assertTrue("[milk]" in text, text)
        assertTrue("# Habita order" in text, text)
    }

    @Test
    fun `days read like a person says them`() {
        assertEquals("Thu", weekdayName(0))
        assertEquals("Thu 1 Jan", dateLabel(0))
        assertEquals("tomorrow", whenLabel(day + 1, day))
        assertEquals("today", whenLabel(day - 1, day))
    }

    @Test
    fun `days with nothing left don't eat into the next purchase`() {
        // 480 g of oats lasts 6 days; the refill comes 10 days later, 4 empty days after.
        val events = listOf(buy("oats", 480.0, day), buy("oats", 1000.0, day + 10))
        val stock = Pantry.of(plan, events, today = day + 10, made = madeEvery(day, day + 10)).first { it.item.id == "oats" }

        assertEquals(1000.0, stock.left)
        assertEquals(12, stock.daysLeft)
    }

    @Test
    fun `over budget reads as over, not as a broken amount`() {
        val pantry = Pantry.of(plan, listOf(buy("milk", 800.0, day)), today = day, made = madeEvery(day, day))
        val text = Refill.request(Refill.lines(pantry), FoodSpend(5_500_00, 5_000_00), day)

        assertTrue("(₹500 over)" in text, text)
        assertEquals("-₹1,259.50", Refill.rupees(-1_259_50))
    }

    private val dinner = Meal("meal-2", "Dinner", null, 10, 300, listOf(Ingredient("milk", "Milk", 200.0, "")), emptyList())
    private val twoMeals = plan.copy(meals = plan.meals + dinner)

    @Test
    fun `a day that wasn't logged takes nothing, and a logged day takes only what was made`() {
        val events = listOf(count("milk", 2000.0, day))
        val made = mapOf(day + 2 to setOf("meal-2"), day + 3 to emptySet())
        val stock = Pantry.of(twoMeals, events, today = day + 3, made = made).first { it.item.id == "milk" }

        // Day 1 wasn't logged, day 2 only dinner (200 ml), day 3 nothing.
        assertEquals(1800.0, stock.left)
    }

    @Test
    fun `skips from older apps don't add stock back`() {
        val skip = StockEvent("skip", "milk", StockEvent.Kind.ADD, 400.0, day + 1, 1, note = "Skipped Breakfast")
        val stock = Pantry.of(plan, listOf(count("milk", 2000.0, day), skip), today = day + 1).first { it.item.id == "milk" }

        assertEquals(2000.0, stock.left)
    }

    @Test
    fun `running out counts today's meals as still to come until today is logged`() {
        val events = listOf(count("milk", 2000.0, day))

        // 2,000 ml at 400 a day: today's 400 still to come, so 4 more days after it.
        val open = Pantry.of(plan, events, today = day + 1).first { it.item.id == "milk" }
        assertEquals(2000.0, open.left)
        assertEquals(4, open.daysLeft)

        val logged = Pantry.of(plan, events, today = day + 1, made = madeEvery(day + 1, day + 1)).first { it.item.id == "milk" }
        assertEquals(1600.0, logged.left)
        assertEquals(4, logged.daysLeft)
    }

    @Test
    fun `an opened pack is finished by the plan's open days`() {
        val dahi = FoodItem("dahi", "Dahi 1 kg", 1000.0, "g", BuyAt.APP, "Zepto", 80_00, openDays = 3)
        val withDahi = plan.copy(items = plan.items + dahi)
        val pantry = Pantry.of(withDahi, emptyList(), today = day, opened = mapOf("dahi" to day - 2, "milk" to day))

        assertEquals(day + 1, pantry.first { it.item.id == "dahi" }.finishBy)
        // Milk doesn't say how long it keeps open, so its opening isn't tracked.
        assertNull(pantry.first { it.item.id == "milk" }.openedOn)
    }

    @Test
    fun `the day log reads days, opened packs and what to ask about`() {
        val values = mapOf(
            "plan" to "# Food plan",
            "made:${day + 1}" to "meal-1,meal-3",
            "made:${day + 2}" to "",
            "opened:dahi" to "${day + 1}",
            "opened:bread" to "",
        )
        val made = DayLog.made(values)

        assertEquals(mapOf(day + 1 to setOf("meal-1", "meal-3"), day + 2 to emptySet()), made)
        assertEquals(mapOf("dahi" to day + 1), DayLog.opened(values))
        assertEquals("meal-1,meal-3", DayLog.encode(setOf("meal-3", "meal-1")))
        // Counted on day 0; days 1 and 2 are logged, 3 and 4 aren't, today (5) is asked separately.
        assertEquals(listOf(day + 3, day + 4), DayLog.unlogged(made, firstDay = day, today = day + 5))
        // At most a week back.
        assertEquals((day + 23 until day + 30).toList(), DayLog.unlogged(emptyMap(), firstDay = day, today = day + 30))
        assertEquals(emptyList(), DayLog.unlogged(emptyMap(), firstDay = null, today = day))
    }
}