package dev.habita.app.feature.expense.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.HabitaIcons
import dev.habita.app.core.ui.IconBadge
import dev.habita.app.core.ui.LabeledField
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.TopBar
import dev.habita.app.feature.expense.data.Category
import dev.habita.app.feature.expense.data.formatRupees
import dev.habita.app.feature.expense.data.parseRupees

/**
 * Paying a scanned UPI QR: who, how much, what for and the category, then the UPI app takes over
 * for the PIN. The bank SMS that follows is saved with these details.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PayScreen(
    draft: PayDraft,
    categories: List<Category>,
    onBack: () -> Unit,
    onChange: ((PayDraft) -> PayDraft) -> Unit,
    onPay: (amountPaise: Long) -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val amount = parseRupees(draft.amountText)
    var paying by remember(draft.request) { mutableStateOf(false) }
    // With no amount in the QR, the keyboard comes up on the amount.
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!draft.amountFixed) amountFocus.requestFocus() }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen, vertical = Space.inside),
        ) {
            TopBar("Expenses", onBack)
            Gap(Space.inside)
            HText("Pay", type.headline)
            Gap(Space.card)
            HabitaCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(HabitaIcons.Wallet)
                    Gap(Space.item)
                    Column(Modifier.weight(1f)) {
                        HText(draft.request.displayName, type.title, maxLines = 1)
                        HText(draft.request.payeeId, type.caption, color = colors.muted, maxLines = 1)
                    }
                }
            }
            Gap(Space.card)
            if (draft.amountFixed) {
                Overline("Amount")
                Gap(Space.tiny)
                HText(formatRupees(draft.request.amountPaise ?: 0), type.headline)
                HText("Set by the QR code", type.caption, color = colors.muted)
            } else {
                LabeledField(
                    label = "Amount (₹)",
                    value = draft.amountText,
                    onValueChange = { text -> onChange { it.copy(amountText = text.filter { c -> c.isDigit() || c == '.' }) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    fieldModifier = Modifier.focusRequester(amountFocus),
                )
            }
            Gap(Space.card)
            LabeledField(
                label = "What for",
                value = draft.description,
                onValueChange = { text -> onChange { it.copy(description = text) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            Gap(Space.section)
            Overline("Category")
            Gap(Space.item)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.inside), verticalArrangement = Arrangement.spacedBy(Space.inside)) {
                categories.forEach { category ->
                    SmallPill(category.name, selected = category.id == draft.categoryId) {
                        onChange { it.copy(categoryId = category.id) }
                    }
                }
            }
            Gap(Space.card)
            HText(
                "Your UPI app opens next for the PIN. The bank SMS after it is saved with these details.",
                type.caption,
                color = colors.muted,
            )
            Gap(Space.card)
        }
        PrimaryButton(
            text = when {
                amount == null -> "Enter an amount"
                paying -> "Opening UPI app…"
                else -> "Pay ${formatRupees(amount)}"
            },
            onClick = {
                if (amount != null && !paying) {
                    paying = true
                    onPay(amount)
                }
            },
            enabled = amount != null && !paying,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.item),
        )
    }
}