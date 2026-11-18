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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.Chip
import dev.habita.app.core.ui.Divider
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.HabitaIcons
import dev.habita.app.core.ui.IconCircleButton
import dev.habita.app.core.ui.LabeledField
import dev.habita.app.core.ui.LocalWideLayout
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.TextAction
import dev.habita.app.core.ui.TopBar
import dev.habita.app.core.ui.TwoPane
import dev.habita.app.core.ui.appear
import dev.habita.app.feature.food.data.BuyAt
import dev.habita.app.feature.food.data.FoodItem
import dev.habita.app.feature.food.data.ItemStock
import dev.habita.app.feature.food.data.Refill
import dev.habita.app.feature.food.data.formatAmount
import dev.habita.app.feature.food.data.shortDate
import dev.habita.app.feature.food.data.trim
import dev.habita.app.feature.food.data.whenLabel
import kotlin.math.roundToInt

/**
 * What's left of each item, in plain words. Items that need a look (not counted, running low, or
 * a pack to finish soon) come first; the rest sit under "All good". Tapping an item opens one
 * − / + counter for how much is there now, so a purchase is just pressing + (no separate "bought"
 * mode). Items that keep only a few days once open get an "Opened today" tap, and the best-before
 * date hides behind a link. Daily buys, such as bananas, aren't counted: they're a "Buy today" list.
 */
@Composable
internal fun PantryScreen(
    pantry: List<ItemStock>,
    today: Int,
    refillQuestion: String?,
    onBack: () -> Unit,
    onCount: (ItemStock, Double) -> Unit,
    onOpened: (ItemStock, Int) -> Unit,
    onExpires: (ItemStock, String) -> Boolean,
    onRefill: () -> Unit,
    onLogOrder: () -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    var open by remember { mutableStateOf<String?>(null) }
    val daily = Refill.dailyList(pantry)
    val market = Refill.marketList(pantry)
    val counted = pantry.filter { it.item.buyAt != BuyAt.DAILY }
    val (check, fine) = counted.partition { needsLook(it, today) }
    TwoPane(
        Modifier.fillMaxSize(),
        first = {
            TopBar("Meals", onBack)
            Gap(Space.inside)
            HText("Pantry", type.headline)
            Gap(Space.tiny)
            HText(
                if (check.isEmpty()) "All good. Nothing to do." else "${check.size} to look at. Tap one to fix its count.",
                type.body,
                color = colors.muted,
            )
            Gap(Space.item)
            if (refillQuestion != null) {
                PrimaryButton("Refill: see what to order", onRefill, Modifier.fillMaxWidth().appear(0))
                Gap(Space.item)
            }
            if (daily.isNotEmpty()) {
                ShoppingCard("Buy today", daily, Modifier.appear(1))
                Gap(Space.item)
            }
            if (market.isNotEmpty()) {
                ShoppingCard("Market this week", market, Modifier.appear(2))
                Gap(Space.item)
            }
            Row(Modifier.appear(3)) {
                if (refillQuestion == null) TextAction("Order now anyway", onRefill)
                TextAction("Log an order", onLogOrder)
            }
        },
        second = {
            if (LocalWideLayout.current) Gap(56.dp)
            listOf("Look at these" to check, "All good" to fine).forEach { (title, items) ->
                if (items.isEmpty()) return@forEach
                Overline(title, color = if (items === check) colors.accent else colors.muted)
                Gap(Space.item)
                HabitaCard(Modifier.fillMaxWidth()) {
                    items.forEachIndexed { index, stock ->
                        if (index > 0) Divider(Modifier.padding(vertical = Space.item))
                        ItemRow(
                            stock,
                            today,
                            open == stock.item.id,
                            { open = if (open == stock.item.id) null else stock.item.id },
                            { amount -> onCount(stock, amount); open = null },
                            onOpened,
                            onExpires,
                        )
                    }
                }
                Gap(Space.section)
            }
        },
    )
}

@Composable
private fun ShoppingCard(title: String, list: List<Pair<FoodItem, Double>>, modifier: Modifier) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    HabitaCard(modifier.fillMaxWidth()) {
        Overline(title, color = colors.accent)
        list.forEach { (item, amount) ->
            Gap(Space.inside)
            Row {
                HText(Refill.shortName(item), type.body, modifier = Modifier.weight(1f))
                HText(formatAmount(amount, item.unit), type.bodyStrong)
            }
        }
    }
}

@Composable
private fun ColumnScope.ItemRow(
    stock: ItemStock,
    today: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSave: (Double) -> Unit,
    onOpened: (ItemStock, Int) -> Unit,
    onExpires: (ItemStock, String) -> Boolean,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val warn = stock.left == null || stock.left == 0.0 || (stock.daysLeft != null && stock.daysLeft <= Refill.ASK_DAYS) ||
        (stock.useBy?.let { it <= today } == true)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            HText(Refill.shortName(stock.item), type.bodyStrong)
            Gap(Space.tiny)
            HText(status(stock, today), type.caption, color = if (warn) colors.error else colors.muted)
        }
        TextAction(if (expanded) "Close" else "Edit", onToggle)
    }
    if (!expanded) return
    val step = step(stock)
    val start = stock.packsLeft?.let { roundTo(it, step) } ?: 0.0
    var packs by remember(stock.item.id, start) { mutableStateOf(start) }
    Gap(Space.item)
    HText("How much is there now?", type.caption, color = colors.muted)
    Gap(Space.inside)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconCircleButton(HabitaIcons.Minus, "Less", { packs = maxOf(0.0, packs - step) })
        HText(amountText(packs, stock), type.title, modifier = Modifier.weight(1f).padding(horizontal = Space.inside), textAlign = TextAlign.Center)
        IconCircleButton(HabitaIcons.Plus, "More", { packs += step })
    }
    Gap(Space.inside)
    val hint = if (step == 1.0) {
        "Just bought some? Tap + once for each one."
    } else {
        val pack = formatAmount(stock.item.packSize, stock.item.unit)
        "Each + adds ${formatAmount(step * stock.item.packSize, stock.item.unit)}, so a new $pack pack is ${(1 / step).roundToInt()} taps."
    }
    HText(hint, type.caption, color = colors.muted)
    Gap(Space.item)
    PrimaryButton(
        if (packs == 0.0 && (stock.left ?: 0.0) > 0.0) "Save: it's finished" else "Save",
        { onSave(packs) },
        Modifier.fillMaxWidth(),
        enabled = packs != start || stock.left == null,
    )
    if (stock.item.openDays != null) {
        Gap(Space.item)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.inside)) {
            Chip("Opened today", stock.openedOn == today, { onOpened(stock, today) }, Modifier.weight(1f))
            Chip("Yesterday", stock.openedOn == today - 1, { onOpened(stock, today - 1) }, Modifier.weight(1f))
        }
    }
    var dating by remember(stock.item.id) { mutableStateOf(false) }
    Gap(Space.inside)
    if (!dating) {
        TextAction(if (stock.expiresOn == null) "Add best-before date" else "Change best-before date", { dating = true }, color = colors.muted)
    } else {
        var bestBefore by remember(stock.item.id, stock.expiresOn) { mutableStateOf(stock.expiresOn?.let(::shortDate).orEmpty()) }
        var unreadable by remember(stock.item.id) { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.item), verticalAlignment = Alignment.Bottom) {
            LabeledField(
                if (unreadable) "Write it like 14/11" else "Best before (14/11)",
                bestBefore,
                { bestBefore = it; unreadable = false },
                Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            TextAction("Save", {
                unreadable = !onExpires(stock, bestBefore)
                if (!unreadable) dating = false
            })
        }
    }
}

/** Not counted yet, finished, running low, or a pack to finish within a few days. */
private fun needsLook(stock: ItemStock, today: Int): Boolean =
    stock.left == null || stock.left == 0.0 ||
        (stock.daysLeft != null && stock.daysLeft <= Refill.ASK_DAYS) ||
        (stock.useBy?.let { it - today <= Refill.ASK_DAYS } == true)

/**
 * How far one tap of − / + moves, in packs: whole packs for pieces and for small packs the plan
 * eats one or more of a day (milk), quarter packs for big ones such as 1 kg of oats (250 g).
 */
private fun step(stock: ItemStock): Double =
    if (stock.item.packSize == 1.0 || (stock.dailyUse > 0 && stock.item.packSize <= stock.dailyUse)) 1.0 else 0.25

private fun roundTo(value: Double, step: Double): Double = (value / step).roundToInt() * step

/**
 * "None", "6" for pieces, "3 packs" for small packs counted whole (milk), or the weight for big
 * packs counted in quarters: "1.5 kg", "750 g".
 */
private fun amountText(packs: Double, stock: ItemStock): String {
    val item = stock.item
    return when {
        packs <= 0.0 -> "None"
        item.packSize == 1.0 -> trim(packs)
        step(stock) == 1.0 -> "${trim(packs)} pack${if (packs == 1.0) "" else "s"}"
        else -> formatAmount(packs * item.packSize, item.unit)
    }
}

/** "About 1.5 kg left · lasts till Thu", "6 left", "Finished", "Not counted yet: tap Edit", with a pack's date. */
private fun status(stock: ItemStock, today: Int): String {
    val packs = stock.packsLeft ?: return "Not counted yet: tap Edit"
    if (packs <= 0.0) return "Finished"
    val shown = roundTo(packs, step(stock)).takeIf { it > 0 } ?: step(stock)
    val amount = if (step(stock) == 1.0) "${amountText(shown, stock)} left" else "About ${amountText(shown, stock)} left"
    val lasts = when {
        stock.runsOut == null -> null
        stock.daysLeft == 0 -> "runs out today"
        else -> "lasts till ${whenLabel(stock.runsOut, today)}"
    }
    val useBy = stock.useBy?.takeIf { it - today <= Refill.ASK_DAYS }
        ?.let { if (it < today) "past its date" else "use by ${whenLabel(it, today)}" }
    return listOfNotNull(amount, lasts, useBy).joinToString(" · ")
}