package dev.habita.app.feature.food.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.Divider
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.LabeledField
import dev.habita.app.core.ui.LocalWideLayout
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.SecondaryButton
import dev.habita.app.core.ui.TextAction
import dev.habita.app.core.ui.TopBar
import dev.habita.app.core.ui.TwoPane
import dev.habita.app.core.ui.appear
import dev.habita.app.feature.food.data.BuyAt
import dev.habita.app.feature.food.data.ItemStock
import dev.habita.app.feature.food.data.Refill
import dev.habita.app.feature.food.data.formatAmount
import dev.habita.app.feature.food.data.trim
import dev.habita.app.feature.food.data.whenLabel

/**
 * What's left of each item and when it runs out, worked out from the plan. Tap an item to say how
 * much is really left or to add what was bought. The market list is the week's vegetables and
 * fruit; app items come through refills, and an order is logged with "Log an order".
 */
@Composable
internal fun PantryScreen(
    pantry: List<ItemStock>,
    today: Int,
    refillQuestion: String?,
    onBack: () -> Unit,
    onCount: (ItemStock, Double) -> Unit,
    onBought: (ItemStock, Double) -> Unit,
    onRefill: () -> Unit,
    onLogOrder: () -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    var open by remember { mutableStateOf<String?>(null) }
    val market = Refill.marketList(pantry)
    TwoPane(
        Modifier.fillMaxSize(),
        first = {
            TopBar("Meals", onBack)
            Gap(Space.inside)
            HText("Pantry", type.headline)
            Gap(Space.tiny)
            HText("It goes down by the plan every day. Tap an item to fix the count or add what you bought.", type.body, color = colors.muted)
            Gap(Space.item)
            if (refillQuestion != null) PrimaryButton("Refill: see what to order", onRefill, Modifier.fillMaxWidth().appear(0))
            else SecondaryButton("Order now anyway", onRefill, Modifier.fillMaxWidth().appear(0))
            Gap(Space.item)
            SecondaryButton("Log an order", onLogOrder, Modifier.fillMaxWidth().appear(1))
            if (market.isNotEmpty()) {
                Gap(Space.item)
                HabitaCard(Modifier.fillMaxWidth().appear(2)) {
                    Overline("Market this week", color = colors.accent)
                    market.forEach { (item, amount) ->
                        Gap(Space.inside)
                        Row {
                            HText(item.name, type.body, modifier = Modifier.weight(1f))
                            HText(formatAmount(amount, item.unit), type.bodyStrong)
                        }
                    }
                }
            }
        },
        second = {
            if (LocalWideLayout.current) Gap(56.dp)
            listOf(BuyAt.APP to "From apps", BuyAt.MARKET to "From the market").forEach { (buyAt, title) ->
                val items = pantry.filter { it.item.buyAt == buyAt }
                if (items.isEmpty()) return@forEach
                Overline(title)
                Gap(Space.item)
                HabitaCard(Modifier.fillMaxWidth()) {
                    items.forEachIndexed { index, stock ->
                        if (index > 0) Divider(Modifier.padding(vertical = Space.item))
                        ItemRow(stock, today, open == stock.item.id, { open = if (open == stock.item.id) null else stock.item.id }, onCount, onBought)
                    }
                }
                Gap(Space.section)
            }
        },
    )
}

@Composable
private fun ColumnScope.ItemRow(
    stock: ItemStock,
    today: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCount: (ItemStock, Double) -> Unit,
    onBought: (ItemStock, Double) -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val low = stock.daysLeft != null && stock.daysLeft <= Refill.ASK_DAYS
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            HText(stock.item.name, type.bodyStrong)
            Gap(Space.tiny)
            HText(status(stock, today), type.caption, color = if (low) colors.error else colors.muted)
        }
        TextAction(if (expanded) "Close" else "Update", onToggle)
    }
    if (expanded) {
        var left by remember(stock.item.id) { mutableStateOf("") }
        var bought by remember(stock.item.id) { mutableStateOf("") }
        val pack = formatAmount(stock.item.packSize, stock.item.unit)
        Gap(Space.item)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.item), verticalAlignment = Alignment.Bottom) {
            LabeledField(
                "Packs left now (of $pack)",
                left,
                { left = it.filter { c -> c.isDigit() || c == '.' } },
                Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            )
            TextAction("Save", { left.toDoubleOrNull()?.let { onCount(stock, it); left = "" } })
        }
        Gap(Space.item)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.item), verticalAlignment = Alignment.Bottom) {
            LabeledField(
                "Packs bought",
                bought,
                { bought = it.filter { c -> c.isDigit() || c == '.' } },
                Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            )
            TextAction("Add", { bought.toDoubleOrNull()?.takeIf { it > 0 }?.let { onBought(stock, it); bought = "" } })
        }
    }
}

/** "6 packs left · runs out Thu", "Not counted yet" or "Not in any meal". */
private fun status(stock: ItemStock, today: Int): String {
    val packs = stock.packsLeft ?: return "Not counted yet: tap Update"
    val left = if (stock.item.packSize == 1.0) "${trim(packs)} left" else "${trim(packs)} pack${if (packs == 1.0) "" else "s"} left"
    val runsOut = stock.runsOut?.let { if (stock.daysLeft == 0) "out today" else "runs out ${whenLabel(it, today)}" } ?: "not in any meal"
    return "$left · $runsOut"
}