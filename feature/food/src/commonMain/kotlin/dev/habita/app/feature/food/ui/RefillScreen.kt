package dev.habita.app.feature.food.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.Divider
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.LocalWideLayout
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.SecondaryButton
import dev.habita.app.core.ui.Stepper
import dev.habita.app.core.ui.TextAction
import dev.habita.app.core.ui.TopBar
import dev.habita.app.core.ui.TwoPane
import dev.habita.app.core.ui.appear
import dev.habita.app.feature.food.data.FoodSpend
import dev.habita.app.feature.food.data.Refill
import dev.habita.app.feature.food.data.RefillLine
import dev.habita.app.feature.food.data.formatAmount
import dev.habita.app.feature.food.data.whenLabel

/**
 * The refill, asked about first: what's about to run out, what else is low enough to join the
 * same order, and how many packs of each. Habita doesn't order: "Copy for Claude" copies a request
 * to paste into Claude, which compares the delivery apps, fills the cheapest cart and stops before
 * paying. "Not now" asks again tomorrow.
 */
@Composable
internal fun RefillScreen(
    lines: List<RefillLine>,
    spend: FoodSpend?,
    today: Int,
    copied: Boolean,
    onBack: () -> Unit,
    onPacks: (itemId: String, packs: Int) -> Unit,
    onCopy: () -> Unit,
    onNotNow: () -> Unit,
    onLogOrder: () -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val ordering = lines.filter { it.packs > 0 }
    TwoPane(
        Modifier.fillMaxSize(),
        first = {
            TopBar("Meals", onBack)
            Gap(Space.inside)
            HText("Refill", type.headline)
            Gap(Space.tiny)
            HText(
                "Health first, then the cheapest cart with fees. Claude compares the apps and fills the cart; you check it and pay.",
                type.body,
                color = colors.muted,
            )
            Gap(Space.item)
            if (spend != null) {
                HabitaCard(Modifier.fillMaxWidth().appear(0)) {
                    Overline("Food this month")
                    Gap(Space.inside)
                    val limit = if (spend.limitPaise > 0) " of ${Refill.rupees(spend.limitPaise)}" else ""
                    HText("${Refill.rupees(spend.spentPaise)}$limit", type.title)
                    if (spend.limitPaise > 0) {
                        Gap(Space.tiny)
                        val left = spend.limitPaise - spend.spentPaise
                        HText(
                            if (left >= 0) "${Refill.rupees(left)} left" else "${Refill.rupees(-left)} over",
                            type.caption,
                            color = if (left >= 0) colors.muted else colors.error,
                        )
                    }
                }
                Gap(Space.item)
            }
            if (copied) {
                HabitaCard(Modifier.fillMaxWidth().appear(1)) {
                    Overline("Copied", color = colors.accent)
                    Gap(Space.inside)
                    HText("Paste it to Claude. Once you've paid, Claude replies with the order: log it here.", type.body)
                    Gap(Space.item)
                    PrimaryButton("Log the order", onLogOrder, Modifier.fillMaxWidth())
                }
            } else {
                PrimaryButton(
                    if (ordering.isEmpty()) "Nothing to order" else "Copy for Claude",
                    onCopy,
                    Modifier.fillMaxWidth().appear(1),
                    enabled = ordering.isNotEmpty(),
                )
                Gap(Space.item)
                SecondaryButton("Not now", onNotNow, Modifier.fillMaxWidth().appear(2))
            }
        },
        second = {
            if (LocalWideLayout.current) Gap(56.dp)
            if (lines.isEmpty()) {
                HText("Nothing from the apps is running low. Habita will ask when something is.", type.body, color = colors.muted)
                return@TwoPane
            }
            Overline("To order")
            Gap(Space.item)
            HabitaCard(Modifier.fillMaxWidth()) {
                lines.forEachIndexed { index, line ->
                    if (index > 0) Divider(Modifier.padding(vertical = Space.item))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            HText(line.stock.item.name, type.bodyStrong)
                            Gap(Space.tiny)
                            val runsOut = line.stock.runsOut?.let { "runs out ${whenLabel(it, today)}" } ?: "low"
                            HText(
                                "${formatAmount(line.stock.item.packSize, line.stock.item.unit)} packs · $runsOut",
                                type.caption,
                                color = if (line.due) colors.error else colors.muted,
                            )
                        }
                        Stepper(
                            value = line.packs.toString(),
                            onValueChange = { text -> onPacks(line.stock.item.id, text.filter(Char::isDigit).toIntOrNull() ?: 0) },
                            onMinus = { onPacks(line.stock.item.id, line.packs - 1) },
                            onPlus = { onPacks(line.stock.item.id, line.packs + 1) },
                            modifier = Modifier.width(150.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            contentDescription = "Packs of ${line.stock.item.name}",
                        )
                    }
                }
            }
            Gap(Space.item)
            HText("Set packs to 0 to leave an item out.", type.caption, color = colors.muted)
            if (copied) {
                Gap(Space.item)
                TextAction("Copy again", onCopy)
            }
        },
    )
}