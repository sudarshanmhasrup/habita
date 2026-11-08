package dev.habita.app.feature.food.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanTextTest {
    @Test
    fun `the example reads, with tracked and shown-only ingredients`() {
        val text = PlanText.EXAMPLE + """

            ## Lunch

            Time: 1:30 PM
            Protein: ~30g
            Calories: 400

            | Item | Amount |
            | --- | --- |
            | milk | 200 |
            | Salt | a pinch |
        """.trimIndent()
        val plan = assertIs<PlanResult.Ok>(PlanText.parse(text)).plan

        assertEquals(listOf("oats", "milk", "banana"), plan.items.map { it.id })
        assertEquals(BuyAt.MARKET, plan.item("banana")?.buyAt)
        assertEquals(166_00, plan.item("oats")?.pricePaise)
        assertEquals(2, plan.meals.size)
        val lunch = plan.meals[1]
        assertEquals("meal-2", lunch.id)
        assertEquals(30, lunch.proteinGrams)
        assertEquals("a pinch", lunch.ingredients[1].amountText)
        assertNull(lunch.ingredients[1].itemId)
        assertEquals(400.0, plan.dailyUse["milk"])
        assertEquals(45, plan.proteinGrams)
        assertEquals(2, plan.meals[0].steps.size)
        assertTrue(plan.meals[0].steps[0].startsWith("Mix the oats"))
    }

    @Test
    fun `problems are listed instead of saving a broken plan`() {
        val text = """
            ## Items

            | Id | Item | Pack | Unit |
            | --- | --- | --- | --- |
            | oats | Oats | 1000 | kg |
            | milk | Milk | lots | ml |

            ## Breakfast

            | Item | Amount |
            | --- | --- |
            | oats | some |
        """.trimIndent()
        val problems = assertIs<PlanResult.Error>(PlanText.parse(text)).problems

        assertTrue(problems.any { "unit should be g, ml or pc" in it }, problems.toString())
        assertTrue(problems.any { "pack size should be a number" in it }, problems.toString())
    }

    @Test
    fun `a plan needs items and meals`() {
        val problems = assertIs<PlanResult.Error>(PlanText.parse("# Food plan")).problems
        assertEquals(2, problems.size)
    }

    @Test
    fun `amounts read the way people write them`() {
        assertEquals("80 g", formatAmount(80.0, "g"))
        assertEquals("1 kg", formatAmount(1000.0, "g"))
        assertEquals("1.5 L", formatAmount(1500.0, "ml"))
        assertEquals("2.5", trim(2.5))
        assertEquals(1259.0, PlanText.number("₹1,259"))
    }
}