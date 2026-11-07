package dev.habita.app.feature.expense.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.habita.app.core.data.nowMillis
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.Chip
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HIcon
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaIcons
import dev.habita.app.core.ui.LabeledField
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.SecondaryButton
import dev.habita.app.core.ui.TextAction
import dev.habita.app.core.ui.TopBar
import dev.habita.app.feature.expense.data.BankSms
import dev.habita.app.feature.expense.data.Category
import dev.habita.app.feature.expense.data.Defaults
import dev.habita.app.feature.expense.data.ExpenseSource
import dev.habita.app.feature.expense.data.PaymentMode
import dev.habita.app.feature.expense.data.YearMonth
import dev.habita.app.feature.expense.data.dayLabel
import dev.habita.app.feature.expense.data.monthLabel
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

/**
 * [month]'s income and each category's limit, which hold for the months after it too, up to
 * [laterBudget] when a later month has its own. A limit of 0 means no limit. Categories can be
 * renamed, removed and brought back here as well; nothing changes until Save.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BudgetScreen(
    month: YearMonth,
    laterBudget: YearMonth?,
    income: Long,
    categories: List<Category>,
    removed: List<Category>,
    onBack: () -> Unit,
    onSave: (BudgetEdit) -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    var incomeText by remember { mutableStateOf(rupeesText(income)) }
    // Keyed on which categories there are, not the list, so typing survives a sync or a new SMS.
    val everyone = categories + removed
    val ids = everyone.map { it.id }
    val limits = remember(ids) { everyone.associate { it.id to mutableStateOf(rupeesText(it.limitPaise)) } }
    val names = remember(ids) { everyone.associate { it.id to mutableStateOf(it.name) } }
    var removing by remember { mutableStateOf(emptySet<String>()) }
    var bringingBack by remember { mutableStateOf(emptySet<String>()) }
    var newName by remember { mutableStateOf("") }
    var newLimit by remember { mutableStateOf("") }
    val shown = categories.filter { it.id !in removing } + removed.filter { it.id in bringingBack }
    val hidden = removed.filter { it.id !in bringingBack } + categories.filter { it.id in removing }
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
            HText(
                "For ${monthLabel(month)}" +
                    (laterBudget?.let { " until ${monthLabel(it)}, which has its own" } ?: " and the months after it") +
                    ". Earlier months keep theirs. Amounts in rupees; a limit of 0 means no limit.",
                type.body,
                color = colors.muted,
            )
            Gap(Space.card)
            LabeledField(
                label = "Monthly income",
                value = incomeText,
                onValueChange = { incomeText = it.filter(Char::isDigit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Gap(Space.section)
            Overline("Categories")
            Gap(Space.item)
            Column(verticalArrangement = Arrangement.spacedBy(Space.item)) {
                shown.forEach { category ->
                    val name = names.getValue(category.id)
                    val limit = limits.getValue(category.id)
                    Row(verticalAlignment = Alignment.Bottom) {
                        LabeledField(
                            label = "Category",
                            value = name.value,
                            onValueChange = { name.value = it },
                            modifier = Modifier.weight(1.5f),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                        Gap(Space.inside)
                        LabeledField(
                            label = "Limit",
                            value = limit.value,
                            onValueChange = { limit.value = it.filter(Char::isDigit) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        // Other is where everything unsorted can go, so it always stays.
                        if (category.id == Defaults.OTHER_ID) {
                            Gap(BIN_SIZE)
                        } else {
                            Bin("Remove ${category.name}") {
                                if (category.id in bringingBack) bringingBack -= category.id else removing += category.id
                            }
                        }
                    }
                }
            }
            if (hidden.isNotEmpty()) {
                Gap(Space.section)
                Overline("Removed")
                Gap(Space.tiny)
                HText("Old payments keep their category. Bring one back to use it again.", type.caption, color = colors.muted)
                Gap(Space.inside)
                hidden.forEach { category ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        HText(names.getValue(category.id).value.ifBlank { category.name }, type.body, modifier = Modifier.weight(1f), maxLines = 1)
                        TextAction("Bring back", {
                            if (category.id in removing) removing -= category.id else bringingBack += category.id
                        })
                    }
                }
            }
            Gap(Space.section)
            Overline("New category")
            Gap(Space.item)
            LabeledField(
                label = "Name",
                value = newName,
                onValueChange = { newName = it },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
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
                val renamed = everyone.mapNotNull { category ->
                    val name = names.getValue(category.id).value.trim()
                    (category.id to name).takeIf { name.isNotEmpty() && name != category.name }
                }.toMap()
                onSave(
                    BudgetEdit(
                        incomePaise = incomeText.toLongOrNull()?.times(100),
                        limits = shown.associate { it.id to (limits.getValue(it.id).value.toLongOrNull() ?: 0) * 100 },
                        names = renamed,
                        remove = removing,
                        bringBack = bringingBack,
                        newCategory = newName.trim().takeIf { it.isNotEmpty() }?.let { it to (newLimit.toLongOrNull() ?: 0) * 100 },
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.item),
        )
    }
}

private val BIN_SIZE = 48.dp

@Composable
private fun Bin(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(BIN_SIZE)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        HIcon(HabitaIcons.Trash, contentDescription = null, modifier = Modifier.size(18.dp), tint = HabitaTheme.colors.muted)
    }
}

private fun rupeesText(paise: Long): String = (paise / 100).toString()