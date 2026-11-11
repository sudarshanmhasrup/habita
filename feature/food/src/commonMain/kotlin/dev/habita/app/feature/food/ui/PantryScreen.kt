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
import dev.habita.app.core.ui.Chip
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
import dev.habita.app.feature.food.data.dateLabel
import dev.habita.app.feature.food.data.shortDate
import dev.habita.app.feature.food.data.weekdayName
import dev.habita.app.feature.food.data.whenLabel

/**
 * What's left of each item and when it runs out, worked out from the meals made. Tap an item to
 * say how many you have now (or, if you'd rather, add what you bought), the best-before date of
 * the pack in use, and for items that keep only a few days once open, when you opened it. The
 * market list is the week's vegetables and
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
    onOpened: (ItemStock, Int) -> Unit,
    onExpires: (ItemStock, String) -> Boolean,
    onFinished: (ItemStock) -> Unit,
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
            HText("It goes down by the meals you log as made. Tap an item to set how many you have.", type.body, color = colors.muted)
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
                        ItemRow(
                            stock,
                            today,
                            open == stock.item.id,
                            { open = if (open == stock.item.id) null else stock.item.id },
                            onCount,
                            onBought,
                            onOpened,
                            onExpires,
                            onFinished,
                        )
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
    onOpened: (ItemStock, Int) -> Unit,
    onExpires: (ItemStock, String) -> Boolean,
    onFinished: (ItemStock) -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val low = stock.daysLeft != null && stock.daysLeft <= Refill.ASK_DAYS
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            HText(stock.item.name, type.bodyStrong)
            Gap(Space.tiny)
            HText(status(stock, today), type.caption, color = if (low) colors.error else colors.muted)
            dates(stock, today)?.let { text ->
                Gap(Space.tiny)
                HText(text, type.caption, color = if (stock.useBy!! <= today) colors.error else colors.muted)
            }
        }
        TextAction(if (expanded) "Close" else "Update", onToggle)
    }
    if (expanded) {
        // One number at a time: how many you have now, or (switched) how many you just bought.
        var amount by remember(stock.item.id) { mutableStateOf("") }
        var buying by remember(stock.item.id) { mutableStateOf(false) }
        val pieces = stock.item.packSize == 1.0
        val pack = formatAmount(stock.item.packSize, stock.item.unit)
        val label = when {
            buying && pieces -> "How many did you buy?"
            buying -> "Packs you bought (1 pack = $pack)"
            pieces -> "How many do you have now?"
            else -> "Packs you have now (1 pack = $pack)"
        }
        Gap(Space.item)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.item), verticalAlignment = Alignment.Bottom) {
            LabeledField(
                label,
                amount,
                { amount = it.filter { c -> c.isDigit() || c == '.' } },
                Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            )
            TextAction(if (buying) "Add" else "Save", {
                amount.toDoubleOrNull()?.takeIf { !buying || it > 0 }?.let {
                    if (buying) onBought(stock, it) else onCount(stock, it)
                    amount = ""
                }
            })
        }
        Gap(Space.inside)
        TextAction(
            if (buying) "Set how many you have instead" else "Just bought some? Add them instead",
            { buying = !buying; amount = "" },
            color = colors.muted,
        )
        var bestBefore by remember(stock.item.id, stock.expiresOn) { mutableStateOf(stock.expiresOn?.let(::shortDate).orEmpty()) }
        var unreadable by remember(stock.item.id) { mutableStateOf(false) }
        Gap(Space.item)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.item), verticalAlignment = Alignment.Bottom) {
            LabeledField(
                if (unreadable) "Best before: write it like 14/11" else "Best before, as on the pack (14/11)",
                bestBefore,
                { bestBefore = it; unreadable = false },
                Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            TextAction("Save", { unreadable = !onExpires(stock, bestBefore) })
        }
        if (stock.item.openDays != null) {
            Gap(Space.item)
            HText("Opened this pack:", type.caption, color = colors.muted)
            Gap(Space.inside)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.inside)) {
                (0..3).forEach { back ->
                    val day = today - back
                    Chip(
                        when (back) {
                            0 -> "Today"
                            1 -> "Yest."
                            else -> weekdayName(day)
                        },
                        stock.openedOn == day,
                        { onOpened(stock, day) },
                        Modifier.weight(1f),
                    )
                }
            }
        }
        if (stock.openedOn != null || stock.expiresOn != null) {
            Gap(Space.inside)
            TextAction("Pack finished: clear its dates", { onFinished(stock) }, color = colors.muted)
        }
    }
}

/**
 * "Opened Mon · finish by Sat", "Best before Fri" or both, or `null` when the pack in use has
 * neither date.
 */
private fun dates(stock: ItemStock, today: Int): String? {
    val parts = buildList {
        val openedOn = stock.openedOn
        val finishBy = stock.finishBy
        if (openedOn != null && finishBy != null) {
            add(if (openedOn == today) "Opened today" else "Opened ${whenLabelPast(openedOn, today)}")
            add(if (finishBy < today) "past its days" else "finish by ${whenLabel(finishBy, today)}")
        }
        stock.expiresOn?.let { add(if (it < today) "past best before" else "best before ${whenLabel(it, today)}") }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")?.replaceFirstChar { it.uppercase() }
}

/** "yesterday", "Mon" within the week, or "Mon 9 Nov". */
private fun whenLabelPast(day: Int, today: Int): String = when (today - day) {
    1 -> "yesterday"
    in 2..6 -> weekdayName(day)
    else -> dateLabel(day)
}

/** "6 packs left · runs out Thu", "Not counted yet" or "Not in any meal". */
private fun status(stock: ItemStock, today: Int): String {
    val packs = stock.packsLeft ?: return "Not counted yet: tap Update"
    val left = if (stock.item.packSize == 1.0) "${trim(packs)} left" else "${trim(packs)} pack${if (packs == 1.0) "" else "s"} left"
    val runsOut = stock.runsOut?.let { if (stock.daysLeft == 0) "out today" else "runs out ${whenLabel(it, today)}" } ?: "not in any meal"
    return "$left · $runsOut"
}