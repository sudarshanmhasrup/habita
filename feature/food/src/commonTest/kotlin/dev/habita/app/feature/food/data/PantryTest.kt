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

    @Test
    fun `stock goes down by the plan's daily use from the first purchase`() {
        val pantry = Pantry.of(plan, listOf(buy("milk", 4200.0, day, price = 17_00)), today = day + 3)
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
        val stock = Pantry.of(plan, events, today = day + 4).first { it.item.id == "oats" }

        assertEquals(500.0 + 1000.0 - 80.0 * 2, stock.left)
    }

    @Test
    fun `stock never goes below nothing`() {
        val stock = Pantry.of(plan, listOf(buy("milk", 400.0, day)), today = day + 10).first { it.item.id == "milk" }
        assertEquals(0.0, stock.left)
        assertEquals(0, stock.daysLeft)
    }

    @Test
    fun `refills ask for app items about to run out and join the ones running low`() {
        val events = listOf(buy("milk", 800.0, day), buy("oats", 480.0, day), buy("banana", 2.0, day))
        val pantry = Pantry.of(plan, events, today = day)
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
        val pantry = Pantry.of(plan, listOf(buy("milk", 800.0, day), buy("oats", 80.0, day)), today = day)
        assertEquals("Milk and Oats run out tomorrow. Refill?", Refill.question(Refill.due(pantry), day))
    }

    @Test
    fun `the request carries the rules, the budget and the reply format`() {
        val pantry = Pantry.of(plan, listOf(buy("milk", 800.0, day, price = 17_00)), today = day)
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
}