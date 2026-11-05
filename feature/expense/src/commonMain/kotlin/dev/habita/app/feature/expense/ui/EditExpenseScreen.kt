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
import androidx.compose.ui.text.style.TextAlign
import dev.habita.app.core.data.nowMillis
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.Chip
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.LabeledField
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.SecondaryButton
import dev.habita.app.core.ui.TopBar
import dev.habita.app.feature.expense.data.BankSms
import dev.habita.app.feature.expense.data.Category
import dev.habita.app.feature.expense.data.ExpenseSource
import dev.habita.app.feature.expense.data.PaymentMode
import dev.habita.app.feature.expense.data.dayLabel
import dev.habita.app.feature.expense.data.parseRupees
import dev.habita.app.feature.expense.data.startOfDay

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/** Adding a payment by hand, or editing one: amount, what for, category, how it was paid, when. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditExpenseScreen(
    draft: ExpenseDraft,
    categories: List<Category>,
    onBack: () -> Unit,
    onChange: ((ExpenseDraft) -> ExpenseDraft) -> Unit,
    onSave: (amountPaise: Long) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val amount = parseRupees(draft.amountText)
    val fromSms = draft.original?.source == ExpenseSource.SMS
    var confirmDelete by remember(draft.id) { mutableStateOf(false) }
    // A new payment starts with the keyboard up on the amount.
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (draft.id == null) amountFocus.requestFocus() }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen, vertical = Space.inside),
        ) {
            TopBar("Expenses", onBack)
            Gap(Space.inside)
            HText(if (draft.id == null) "Add expense" else "Edit expense", type.headline)
            if (fromSms) {
                Gap(Space.tiny)
                HText("From a bank SMS" + (draft.original?.payee?.let { " · ${BankSms.displayName(it)}" } ?: ""), type.caption, color = colors.muted)
            }
            Gap(Space.card)
            LabeledField(
                label = "Amount (₹)",
                value = draft.amountText,
                onValueChange = { text -> onChange { it.copy(amountText = text.filter { c -> c.isDigit() || c == '.' }) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                fieldModifier = Modifier.focusRequester(amountFocus),
            )
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
            Gap(Space.section)
            Overline("Paid by")
            Gap(Space.item)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.item)) {
                PaymentMode.entries.forEach { mode ->
                    Chip(mode.label, selected = draft.mode == mode, onClick = { onChange { it.copy(mode = mode) } }, modifier = Modifier.weight(1f))
                }
            }
            Gap(Space.section)
            Overline("Date")
            Gap(Space.item)
            DayStepper(draft.paidAt) { paidAt -> onChange { it.copy(paidAt = paidAt) } }
            if (draft.original != null) {
                Gap(Space.section)
                SecondaryButton(
                    text = if (confirmDelete) "Tap again to delete" else "Delete",
                    onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.error,
                )
            }
            Gap(Space.card)
        }
        PrimaryButton(
            text = if (amount == null) "Enter an amount" else "Save",
            onClick = { amount?.let(onSave) },
            enabled = amount != null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.item),
        )
    }
}

/** The payment's day with buttons for the day before and after; never later than today. */
@Composable
private fun DayStepper(paidAt: Long, onChange: (Long) -> Unit) {
    val today = startOfDay(nowMillis())
    val day = startOfDay(paidAt)
    val label = when (day) {
        today -> "Today"
        today - DAY_MILLIS -> "Yesterday"
        else -> dayLabel(paidAt)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SecondaryButton("‹", { onChange(paidAt - DAY_MILLIS) }, Modifier.padding(end = Space.item))
        HText(label, HabitaTheme.typography.bodyStrong, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        SecondaryButton("›", { onChange(paidAt + DAY_MILLIS) }, Modifier.padding(start = Space.item), enabled = day < today)
    }
}

/** The monthly income and each category's limit. A limit of 0 means no limit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BudgetScreen(
    income: Long,
    categories: List<Category>,
    onBack: () -> Unit,
    onSave: (incomePaise: Long?, limits: Map<String, Long>, newCategory: Pair<String, Long>?) -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    var incomeText by remember { mutableStateOf(rupeesText(income)) }
    val limits = remember(categories) { categories.associate { it.id to mutableStateOf(rupeesText(it.limitPaise)) } }
    var newName by remember { mutableStateOf("") }
    var newLimit by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen, vertical = Space.inside),
        ) {
            TopBar("Expenses", onBack)
            Gap(Space.inside)
            HText("Budget", type.headline)
            Gap(Space.tiny)
            HText("Monthly amounts in rupees. Leave a limit at 0 for no limit.", type.body, color = colors.muted)
            Gap(Space.card)
            LabeledField(
                label = "Monthly income",
                value = incomeText,
                onValueChange = { incomeText = it.filter(Char::isDigit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Gap(Space.section)
            Overline("Limits")
            Gap(Space.item)
            Column(verticalArrangement = Arrangement.spacedBy(Space.item)) {
                categories.forEach { category ->
                    val state = limits.getValue(category.id)
                    LabeledField(
                        label = category.name,
                        value = state.value,
                        onValueChange = { state.value = it.filter(Char::isDigit) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
            Gap(Space.section)
            Overline("New category")
            Gap(Space.item)
            LabeledField(label = "Name", value = newName, onValueChange = { newName = it })
            Gap(Space.item)
            LabeledField(
                label = "Limit",
                value = newLimit,
                onValueChange = { newLimit = it.filter(Char::isDigit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Gap(Space.card)
        }
        PrimaryButton(
            text = "Save budget",
            onClick = {
                val changed = categories.mapNotNull { category ->
                    val paise = (limits.getValue(category.id).value.toLongOrNull() ?: 0) * 100
                    (category.id to paise).takeIf { paise != category.limitPaise }
                }.toMap()
                val newCategory = newName.trim().takeIf { it.isNotEmpty() }?.let { it to (newLimit.toLongOrNull() ?: 0) * 100 }
                onSave(incomeText.toLongOrNull()?.times(100), changed, newCategory)
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.item),
        )
    }
}

private fun rupeesText(paise: Long): String = (paise / 100).toString()