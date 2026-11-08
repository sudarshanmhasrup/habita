package dev.habita.app.feature.food.ui

import androidx.compose.foundation.layout.Arrangement
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
import dev.habita.app.feature.food.data.Meal

/**
 * The day's meals in order, each opening its recipe, with the day's protein and calories above
 * and the refill question when it stands. Without a plan yet, it explains how to add one.
 */
@Composable
internal fun MealsScreen(
    plan: FoodPlan?,
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

/** 2210 → "2,210". */
internal fun grouped(value: Int): String = value.toString().reversed().chunked(3).joinToString(",").reversed()

private fun macros(meal: Meal): String =
    listOfNotNull(meal.proteinGrams?.let { "$it g protein" }, meal.calories?.let { "$it kcal" }).joinToString(" · ")

/** One meal's recipe: what goes in, how much, and the steps. "Skipped it" puts it back. */
@Composable
internal fun RecipeScreen(meal: Meal, onBack: () -> Unit, onSkip: () -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    var confirming by remember(meal.id) { mutableStateOf(false) }
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
                Gap(Space.item)
            }
            if (meal.ingredients.any { it.itemId != null }) {
                if (confirming) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HText("Put today's ingredients back in the pantry?", type.label, modifier = Modifier.weight(1f))
                        TextAction("Cancel", { confirming = false }, color = colors.muted)
                        TextAction("Yes", { confirming = false; onSkip() })
                    }
                } else {
                    SecondaryButton("Skipped it today", { confirming = true }, Modifier.fillMaxWidth())
                }
            }
        },
    )
}