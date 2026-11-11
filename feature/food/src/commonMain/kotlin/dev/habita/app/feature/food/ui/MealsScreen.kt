package dev.habita.app.feature.food.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.Chip
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.LocalWideLayout
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.SecondaryButton
import dev.habita.app.core.ui.StatTile
import dev.habita.app.core.ui.TextAction
import dev.habita.app.core.ui.TopBar
import dev.habita.app.core.ui.TwoPane
import dev.habita.app.core.ui.appear
import dev.habita.app.feature.food.data.FoodPlan
import dev.habita.app.feature.food.data.ItemStock
import dev.habita.app.feature.food.data.Meal
import dev.habita.app.feature.food.data.dateLabel
import dev.habita.app.feature.food.data.whenLabel

/**
 * The day's meals in order, each opening its recipe, with the day's protein and calories above,
 * the question of what was made (today, or a past day that wasn't logged), packs to use up, and
 * the refill question when it stands. Without a plan yet, it explains how to add one.
 */
@Composable
internal fun MealsScreen(
    plan: FoodPlan?,
    today: Int?,
    made: Map<Int, Set<String>>,
    unlogged: List<Int>,
    pantry: List<ItemStock>?,
    onLogDay: (Int, Set<String>) -> Unit,
    refillQuestion: String?,
    onBack: () -> Unit,
    onMeal: (Meal) -> Unit,
    onPantry: () -> Unit,
    onRefill: () -> Unit,
    onPlan: () -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val wide = LocalWideLayout.current
    TwoPane(
        Modifier.fillMaxSize(),
        first = {
            TopBar("Home", onBack) { if (plan != null) TextAction("Edit plan", onPlan) }
            Gap(Space.inside)
            HText("Food", type.headline)
            Gap(Space.tiny)
            HText("Every meal, its recipe, and what's left in the pantry.", type.body, color = colors.muted)
            Gap(Space.item)
            if (plan == null) {
                HabitaCard(Modifier.fillMaxWidth().appear(0)) {
                    HText("No plan yet", type.title)
                    Gap(Space.inside)
                    HText(
                        "Write your meals and pantry items as a short markdown plan, or paste one from Claude. Habita works out the rest.",
                        type.body,
                        color = colors.muted,
                    )
                    Gap(Space.item)
                    PrimaryButton("Add a plan", onPlan, Modifier.fillMaxWidth())
                }
                return@TwoPane
            }
            Row(Modifier.appear(0), horizontalArrangement = Arrangement.spacedBy(Space.item)) {
                StatTile("Protein", "${plan.proteinGrams} g", Modifier.weight(1f), detail = "a day")
                StatTile("Calories", grouped(plan.calories), Modifier.weight(1f), detail = "kcal a day")
            }
            Gap(Space.item)
            if (today != null) {
                DayLogCard(plan.meals, today, made, unlogged, onLogDay, Modifier.appear(1))
                Gap(Space.item)
                val useUp = pantry.orEmpty().filter { stock ->
                    val useBy = stock.useBy ?: return@filter false
                    stock.left != 0.0 && (stock.finishBy != null || useBy - today <= USE_UP_DAYS)
                }
                if (useUp.isNotEmpty()) {
                    UseUpCard(useUp, today, Modifier.appear(1))
                    Gap(Space.item)
                }
            }
            if (refillQuestion != null) {
                HabitaCard(Modifier.fillMaxWidth().appear(1)) {
                    Overline("Refill", color = colors.accent)
                    Gap(Space.inside)
                    HText(refillQuestion, type.title)
                    Gap(Space.item)
                    PrimaryButton("See what to order", onRefill, Modifier.fillMaxWidth())
                }
                Gap(Space.item)
            }
            SecondaryButton("Pantry", onPantry, Modifier.fillMaxWidth().appear(2))
        },
        second = {
            if (plan == null) return@TwoPane
            if (wide) Gap(56.dp)
            plan.meals.forEachIndexed { index, meal ->
                MealCard(meal, Modifier.appear(index + 3)) { onMeal(meal) }
                Gap(Space.item)
            }
        },
    )
}

@Composable
private fun MealCard(meal: Meal, modifier: Modifier, onClick: () -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    HabitaCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline(meal.time ?: "Meal", Modifier.weight(1f), color = colors.accent)
            HText(macros(meal), type.caption, color = colors.muted)
        }
        Gap(Space.inside)
        HText(meal.name, type.title)
        Gap(Space.tiny)
        HText(meal.ingredients.joinToString(" · ") { it.label }, type.body, color = colors.muted, maxLines = 2)
    }
}

/**
 * What was made on a day, as a tick for each meal. It asks about the oldest past day that wasn't
 * logged first, then today; once today is logged it folds to one line with "Change".
 */
@Composable
private fun DayLogCard(
    meals: List<Meal>,
    today: Int,
    made: Map<Int, Set<String>>,
    unlogged: List<Int>,
    onLogDay: (Int, Set<String>) -> Unit,
    modifier: Modifier,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    var changing by remember(today) { mutableStateOf(false) }
    val day = unlogged.firstOrNull() ?: today
    val logged = made[day]
    HabitaCard(modifier.fillMaxWidth()) {
        if (day == today && logged != null && !changing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Overline("Today", color = colors.accent)
                    Gap(Space.tiny)
                    HText("${logged.count { id -> meals.any { it.id == id } }} of ${meals.size} meals made", type.title)
                }
                TextAction("Change", { changing = true })
            }
            return@HabitaCard
        }
        var picked by remember(day, logged) { mutableStateOf(logged ?: meals.map { it.id }.toSet()) }
        Overline(if (day == today) "Today" else "Not logged yet", color = colors.accent)
        Gap(Space.inside)
        HText(if (day == today) "What did you make today?" else "What did you make on ${dateLabel(day)}?", type.title)
        Gap(Space.tiny)
        HText(
            if (unlogged.size > 1) "${unlogged.size} days to catch up. Untick what you didn't make." else "Untick what you didn't make. Only these come off the pantry.",
            type.body,
            color = colors.muted,
        )
        meals.forEach { meal ->
            Gap(Space.inside)
            Chip(
                listOfNotNull(meal.time, shortName(meal)).joinToString(" · "),
                meal.id in picked,
                { picked = if (meal.id in picked) picked - meal.id else picked + meal.id },
                Modifier.fillMaxWidth(),
            )
        }
        Gap(Space.item)
        PrimaryButton("Save", { onLogDay(day, picked); changing = false }, Modifier.fillMaxWidth())
        if (day != today) {
            Gap(Space.inside)
            TextAction("Made nothing that day", { onLogDay(day, emptySet()) })
        }
    }
}

/** How close a best-before date has to be for the meals screen to mention it. */
private const val USE_UP_DAYS = 3

/**
 * Packs to use up soon: opened ones that only keep a few days, such as dahi, and any nearing their
 * best-before date, each with the day to finish it by.
 */
@Composable
private fun UseUpCard(items: List<ItemStock>, today: Int, modifier: Modifier) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    HabitaCard(modifier.fillMaxWidth()) {
        Overline("Use up", color = colors.accent)
        items.forEach { stock ->
            val finishBy = stock.useBy ?: return@forEach
            Gap(Space.inside)
            Row {
                HText(stock.item.name, type.body, modifier = Modifier.weight(1f))
                HText(
                    if (finishBy < today) "past its days" else "finish by ${whenLabel(finishBy, today)}",
                    type.bodyStrong,
                    color = if (finishBy <= today) colors.error else colors.text,
                )
            }
        }
    }
}

/** "Meal 1: Breakfast - Overnight oats" → "Breakfast". */
private fun shortName(meal: Meal): String = meal.name.substringAfter(':').substringBefore(" - ").trim().ifEmpty { meal.name }

/** 2210 → "2,210". */
internal fun grouped(value: Int): String = value.toString().reversed().chunked(3).joinToString(",").reversed()

private fun macros(meal: Meal): String =
    listOfNotNull(meal.proteinGrams?.let { "$it g protein" }, meal.calories?.let { "$it kcal" }).joinToString(" · ")

/** One meal's recipe: what goes in, how much, and the steps. */
@Composable
internal fun RecipeScreen(meal: Meal, onBack: () -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    TwoPane(
        Modifier.fillMaxSize(),
        first = {
            TopBar("Meals", onBack)
            Gap(Space.inside)
            meal.time?.let {
                Overline(it, color = colors.accent)
                Gap(Space.tiny)
            }
            HText(meal.name, type.headline)
            if (macros(meal).isNotEmpty()) {
                Gap(Space.tiny)
                HText(macros(meal), type.body, color = colors.muted)
            }
            Gap(Space.card)
            HabitaCard(Modifier.fillMaxWidth().appear(0)) {
                Overline("What goes in")
                meal.ingredients.forEach { ingredient ->
                    Gap(Space.item)
                    Row {
                        HText(ingredient.label, type.body, modifier = Modifier.weight(1f))
                        HText(ingredient.amountText, type.bodyStrong)
                    }
                }
            }
        },
        second = {
            if (LocalWideLayout.current) Gap(56.dp)
            if (meal.steps.isNotEmpty()) {
                HabitaCard(Modifier.fillMaxWidth().appear(1)) {
                    Overline("Steps")
                    meal.steps.forEachIndexed { index, step ->
                        Gap(Space.item)
                        Row {
                            HText("${index + 1}.", type.bodyStrong, color = colors.accent)
                            Gap(Space.inside)
                            HText(step, type.body)
                        }
                    }
                }
            }
        },
    )
}