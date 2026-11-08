package dev.habita.app.feature.expense.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import dev.habita.app.feature.expense.data.MAX_PAY_DAY
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
    // A split saves only when every part is filled in and they add up to the amount.
    val partsOk = draft.parts.isEmpty() || draft.savedParts()?.sumOf { it.amountPaise } == amount
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
            if (draft.parts.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Overline("Category", Modifier.weight(1f))
                    // A grocery order with a kettle in it: each part in its own category.
                    TextAction("Split", {
                        onChange {
                            it.copy(parts = listOf(PartDraft(it.categoryId, it.amountText), PartDraft()))
                        }
                    })
                }
                Gap(Space.item)
                CategoryPills(categories, draft.categoryId) { id -> onChange { it.copy(categoryId = id) } }
            } else {
                SplitParts(draft, categories, amount, onChange)
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
            text = when {
                amount == null -> "Enter an amount"
                !partsOk -> "Parts must add up"
                else -> "Save"
            },
            onClick = { amount?.let(onSave) },
            enabled = amount != null && partsOk,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.item),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPills(categories: List<Category>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.inside), verticalArrangement = Arrangement.spacedBy(Space.inside)) {
        categories.forEach { category ->
            SmallPill(category.name, selected = category.id == selected) { onSelect(category.id) }
        }
    }
}

/**
 * A split payment's parts, each with an amount, what it was and a category, and what they add up
 * to against the payment. "One category" goes back to an unsplit payment.
 */
@Composable
private fun ColumnScope.SplitParts(
    draft: ExpenseDraft,
    categories: List<Category>,
    amount: Long?,
    onChange: ((ExpenseDraft) -> ExpenseDraft) -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    fun update(index: Int, change: (PartDraft) -> PartDraft) = onChange { draft ->
        draft.copy(parts = draft.parts.mapIndexed { i, part -> if (i == index) change(part) else part })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Overline("Split", Modifier.weight(1f))
        TextAction("One category", {
            onChange { it.copy(parts = emptyList(), categoryId = it.parts.firstOrNull()?.categoryId ?: it.categoryId) }
        })
    }
    draft.parts.forEachIndexed { index, part ->
        Gap(Space.item)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.item)) {
            LabeledField(
                label = "Part ${index + 1} (₹)",
                value = part.amountText,
                onValueChange = { text -> update(index) { it.copy(amountText = text.filter { c -> c.isDigit() || c == '.' }) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
            )
            LabeledField(
                label = "What",
                value = part.note,
                onValueChange = { text -> update(index) { it.copy(note = text) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1.4f),
            )
        }
        Gap(Space.inside)
        CategoryPills(categories, part.categoryId) { id -> update(index) { it.copy(categoryId = id) } }
        if (draft.parts.size > 2) {
            TextAction("Remove part ${index + 1}", { onChange { it.copy(parts = it.parts.filterIndexed { i, _ -> i != index }) } }, color = colors.muted)
        }
    }
    Gap(Space.item)
    val total = draft.parts.sumOf { parseRupees(it.amountText) ?: 0L }
    Row(verticalAlignment = Alignment.CenterVertically) {
        HText(
            "Parts ₹${amountText(total)} of ₹${amount?.let(::amountText) ?: "0"}",
            type.caption,
            color = if (amount != null && total == amount) colors.muted else colors.error,
            modifier = Modifier.weight(1f),
        )
        TextAction("Add part", { onChange { it.copy(parts = it.parts + PartDraft()) } })
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
    payDay: Int,
    income: Long,
    categories: List<Category>,
    removed: List<Category>,
    onBack: () -> Unit,
    onSave: (BudgetEdit) -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    var incomeText by remember { mutableStateOf(rupeesText(income)) }
    var payDayText by remember { mutableStateOf(payDay.toString()) }
    val payDayValue = payDayText.toIntOrNull()?.takeIf { it in 1..MAX_PAY_DAY }
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
            Gap(Space.item)
            LabeledField(
                label = "Salary day",
                value = payDayText,
                onValueChange = { payDayText = it.filter(Char::isDigit).take(2) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Gap(Space.tiny)
            HText(
                if (payDayValue == null) {
                    "A day from 1 to $MAX_PAY_DAY."
                } else if (payDayValue == 1) {
                    "Months run from the 1st to the last day. Every month, not just this one."
                } else {
                    "Each month runs from the ${ordinal(payDayValue)} to the day before the next ${ordinal(payDayValue)}. Every month, not just this one."
                },
                type.caption,
                color = if (payDayValue == null) colors.error else colors.muted,
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
            text = if (payDayValue == null) "Enter a salary day" else "Save budget",
            enabled = payDayValue != null,
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
                        payDay = payDayValue,
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

/** "1st", "2nd", "5th", "22nd". */
private fun ordinal(day: Int): String = day.toString() + when {
    day % 100 in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}