package dev.habita.app.feature.food.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.TextAction
import dev.habita.app.core.ui.TopBar
import dev.habita.app.feature.food.data.Order
import dev.habita.app.feature.food.data.OrderPayment
import dev.habita.app.feature.food.data.OrderResult
import dev.habita.app.feature.food.data.OrderText
import dev.habita.app.feature.food.data.PlanText
import dev.habita.app.feature.food.data.Refill
import dev.habita.app.feature.food.data.trim

/** A tall text box for pasting markdown, in the app's field style. */
@Composable
private fun TextArea(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val colors = HabitaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = HabitaTheme.typography.caption.copy(color = colors.text),
        cursorBrush = SolidColor(colors.accent),
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
            .background(colors.card, RoundedCornerShape(16.dp))
            .border(if (focused) 1.5.dp else 1.dp, if (focused) colors.accent else colors.border, RoundedCornerShape(16.dp)),
        decorationBox = { inner ->
            Box(Modifier.padding(16.dp)) {
                if (value.isEmpty()) HText(placeholder, HabitaTheme.typography.caption, color = colors.muted)
                inner()
            }
        },
    )
}

/** Problems as a list, in the error colour. */
@Composable
private fun ColumnScope.Problems(problems: List<String>) {
    if (problems.isEmpty()) return
    Gap(Space.item)
    problems.forEach { problem ->
        HText("• $problem", HabitaTheme.typography.caption, color = HabitaTheme.colors.error)
        Gap(Space.tiny)
    }
}

/**
 * The plan as markdown: the pantry items and every meal with its recipe. It's meant to be written
 * once, or changed by asking Claude and pasting the result back; Habita checks it before saving.
 */
@Composable
internal fun PlanScreen(text: String?, problems: List<String>, onBack: () -> Unit, onSave: (String) -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    var draft by remember(text) { mutableStateOf(text.orEmpty()) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen, vertical = Space.inside)) {
            TopBar("Meals", onBack) { if (draft.isEmpty()) TextAction("Start from an example", { draft = PlanText.EXAMPLE }) }
            Gap(Space.inside)
            HText("Food plan", type.headline)
            Gap(Space.tiny)
            HText(
                "An Items table (Id, Item, Pack, Unit, Buy app or market, Shop, Price per pack), then a \"## \" section per meal with " +
                    "Time, Protein, Calories, an Item and Amount table, and Steps. Amounts are in the item's unit.",
                type.caption,
                color = colors.muted,
            )
            Gap(Space.item)
            TextArea(draft, { draft = it }, "Paste your plan here")
            Problems(problems)
            Gap(Space.card)
        }
        PrimaryButton(
            "Save plan",
            { onSave(draft) },
            Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.item),
            enabled = draft.isNotBlank(),
        )
    }
}

/**
 * Logging an order: paste what Claude wrote back after filling the cart, check what it will do,
 * and log it. Pantry items get their packs; the money goes to the expense tracker split by
 * category, matched to the bank SMS when there is one.
 */
@Composable
internal fun OrderScreen(result: OrderResult?, onBack: () -> Unit, onRead: (String) -> Unit, onLog: (Order) -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    var draft by remember { mutableStateOf("") }
    val order = (result as? OrderResult.Ok)?.order
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen, vertical = Space.inside)) {
            TopBar("Back", onBack) { if (draft.isEmpty()) TextAction("See the format", { draft = OrderText.TEMPLATE; onRead(draft) }) }
            Gap(Space.inside)
            HText("Log an order", type.headline)
            Gap(Space.tiny)
            HText("Paste the order Claude wrote after filling the cart.", type.body, color = colors.muted)
            Gap(Space.item)
            TextArea(draft, { draft = it; onRead(it) }, "# Habita order …")
            Problems((result as? OrderResult.Error)?.problems.orEmpty())
            if (order != null) {
                Gap(Space.item)
                HabitaCard(Modifier.fillMaxWidth()) {
                    Overline("${order.shop} · ${paidLabel(order.payment)}", color = colors.accent)
                    order.lines.forEach { line ->
                        Gap(Space.inside)
                        Row {
                            val pantry = if (line.itemId != null) " · pantry +${trim(line.quantity)}" else ""
                            HText("${line.name} (${line.category}$pantry)", type.body, modifier = Modifier.weight(1f))
                            Gap(Space.inside)
                            HText(Refill.rupees(line.pricePaise), type.bodyStrong)
                        }
                    }
                    Gap(Space.item)
                    Row {
                        HText("Total", type.title, modifier = Modifier.weight(1f))
                        HText(Refill.rupees(order.totalPaise), type.title)
                    }
                }
            }
            Gap(Space.card)
        }
        PrimaryButton(
            if (order == null) "Paste an order" else "Log order",
            { order?.let(onLog) },
            Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.item),
            enabled = order != null,
        )
    }
}

private fun paidLabel(payment: OrderPayment): String = when (payment) {
    OrderPayment.BANK -> "paid by bank, split when its SMS is in"
    OrderPayment.WALLET -> "paid from a wallet"
    OrderPayment.CASH -> "paid in cash"
}