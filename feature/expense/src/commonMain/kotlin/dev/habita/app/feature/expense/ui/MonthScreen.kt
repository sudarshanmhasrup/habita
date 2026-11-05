package dev.habita.app.feature.expense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.ControlShape
import dev.habita.app.core.ui.Divider
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HIcon
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.HabitaIcons
import dev.habita.app.core.ui.LocalWideLayout
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.ProgressBar
import dev.habita.app.core.ui.TextAction
import dev.habita.app.core.ui.TopBar
import dev.habita.app.core.ui.TwoPane
import dev.habita.app.feature.expense.data.Category
import dev.habita.app.feature.expense.data.CategorySpend
import dev.habita.app.feature.expense.data.Expense
import dev.habita.app.feature.expense.data.ExpenseSource
import dev.habita.app.feature.expense.data.MonthSummary
import dev.habita.app.feature.expense.data.YearMonth
import dev.habita.app.feature.expense.data.dayLabel
import dev.habita.app.feature.expense.data.formatRupees
import dev.habita.app.feature.expense.data.monthLabel
import dev.habita.app.feature.expense.data.startOfDay
import dev.habita.app.feature.expense.data.timeLabel
import kotlinx.coroutines.delay

/** One month: what's left, payments to sort, each category against its limit, every payment. */
@Composable
internal fun MonthScreen(
    month: YearMonth,
    summary: MonthSummary?,
    sms: SmsAccess,
    message: String?,
    deleted: Expense?,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onSort: (expenseId: String, categoryId: String) -> Unit,
    onBudget: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onDismissMessage: () -> Unit,
    onUndo: () -> Unit,
    onDismissUndo: () -> Unit,
) {
    val type = HabitaTheme.typography
    val wide = LocalWideLayout.current
    Column(Modifier.fillMaxSize()) {
        TwoPane(
            Modifier.weight(1f),
            first = {
                TopBar("Home", onBack)
                Gap(Space.inside)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HText("Expenses", type.headline, modifier = Modifier.weight(1f))
                    if (summary?.expenses?.isNotEmpty() == true) TextAction("Export", onExport)
                }
                Gap(Space.item)
                MonthSwitcher(month, canGoBack, canGoForward, onPrevious, onNext)
                Gap(Space.item)
                if (summary != null) Overview(summary)
                if (sms.supported && !canGoForward) {
                    Gap(Space.item)
                    SmsCard(sms, onImport)
                }
                if (summary != null && summary.unsorted.isNotEmpty()) {
                    Gap(Space.section)
                    ToSort(summary, onOpen, onSort)
                }
            },
            second = {
                if (wide) Gap(56.dp)
                if (summary != null) {
                    Categories(summary, onBudget)
                    Gap(Space.section)
                    Payments(summary, onOpen)
                    Gap(Space.card)
                }
            },
        )
        Column(
            Modifier
                .align(Alignment.End)
                .fillMaxWidth(if (wide) 0.5f else 1f)
                .padding(horizontal = Space.screen, vertical = Space.item),
        ) {
            // The undo offer goes away on its own after a few seconds.
            LaunchedEffect(deleted) {
                if (deleted != null) {
                    delay(6_000)
                    onDismissUndo()
                }
            }
            val note: Triple<String, String, () -> Unit>? = when {
                deleted != null -> Triple("Deleted ${deleted.description}", "Undo", onUndo)
                message != null -> Triple(message, "OK", onDismissMessage)
                else -> null
            }
            if (note != null) {
                val (text, action, onAction) = note
                HabitaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = Space.card, end = Space.inside, top = 4.dp, bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HText(text, type.label, modifier = Modifier.weight(1f), maxLines = 2)
                        TextAction(action, onAction)
                    }
                }
                Gap(Space.inside)
            }
            PrimaryButton("Add expense", onAdd, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MonthSwitcher(month: YearMonth, canGoBack: Boolean, canGoForward: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundArrow(HabitaIcons.ChevronLeft, "Previous month", canGoBack, onPrevious)
        HText(monthLabel(month), HabitaTheme.typography.bodyStrong, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        RoundArrow(HabitaIcons.ChevronRight, "Next month", canGoForward, onNext)
    }
}

@Composable
private fun RoundArrow(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = HabitaTheme.colors
    Box(
        Modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(CircleShape)
            .background(colors.card)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        HIcon(icon, label, Modifier.size(18.dp))
    }
}

/** What's left of the income, and how much went out. */
@Composable
private fun Overview(summary: MonthSummary) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    HabitaCard(Modifier.fillMaxWidth()) {
        Overline("Left from income")
        Gap(Space.tiny)
        HText(
            formatRupees(summary.leftPaise),
            type.timer.copy(fontSize = type.display.fontSize * 1.25f, lineHeight = type.display.lineHeight * 1.2f),
            color = if (summary.leftPaise < 0) colors.error else colors.text,
        )
        Gap(Space.tiny)
        HText(
            "Spent ${formatRupees(summary.spentPaise)} of ${formatRupees(summary.incomePaise)}",
            type.body,
            color = colors.muted,
        )
        Gap(Space.item)
        ProgressBar(
            if (summary.incomePaise > 0) summary.spentPaise.toFloat() / summary.incomePaise else 0f,
            height = 6.dp,
            color = if (summary.leftPaise < 0) colors.error else colors.accent,
        )
    }
}

/** Asks for SMS access, or offers to catch up on this month's bank SMS. */
@Composable
private fun ColumnScope.SmsCard(sms: SmsAccess, onImport: () -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    if (!sms.granted) {
        HabitaCard(Modifier.fillMaxWidth(), borderColor = colors.accent) {
            HText("Track payments from bank SMS", type.title)
            Gap(Space.tiny)
            HText(
                "Each bank debit SMS becomes a payment, with a notification to pick its category. The SMS never leave the phone.",
                type.caption,
                color = colors.muted,
            )
            Gap(Space.item)
            PrimaryButton("Turn on", sms::request, Modifier.fillMaxWidth())
        }
    } else {
        if (!sms.canOpenOnPayment) {
            HabitaCard(Modifier.fillMaxWidth(), borderColor = colors.accent) {
                HText("Open Habita after each payment", type.title)
                Gap(Space.tiny)
                HText(
                    "A payment to someone new opens this screen right away, ready to record. Turn on \"Allow display over other apps\" for Habita.",
                    type.caption,
                    color = colors.muted,
                )
                Gap(Space.item)
                PrimaryButton("Allow", sms::requestOpenOnPayment, Modifier.fillMaxWidth())
            }
            Gap(Space.inside)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HText("Bank SMS tracking is on", type.caption, color = colors.muted, modifier = Modifier.weight(1f))
            TextAction("Scan this month's SMS", onImport)
        }
    }
}

/** Payments from SMS that still need a category: one tap on a category sorts each. */
@Composable
private fun ColumnScope.ToSort(summary: MonthSummary, onOpen: (String) -> Unit, onSort: (String, String) -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val choices = sortChoices(summary)
    Overline("To sort · ${summary.unsorted.size}", color = colors.accent)
    Gap(Space.item)
    Column(verticalArrangement = Arrangement.spacedBy(Space.inside)) {
        summary.unsorted.forEach { expense ->
            HabitaCard(Modifier.fillMaxWidth(), onClick = { onOpen(expense.id) }, contentPadding = PaddingValues(vertical = Space.card)) {
                Row(Modifier.padding(horizontal = Space.card), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        HText(expense.description, type.bodyStrong, maxLines = 1)
                        HText("${dayLabel(expense.paidAt)} · ${timeLabel(expense.paidAt)}", type.caption, color = colors.muted)
                    }
                    HText(formatRupees(expense.amountPaise), type.title)
                }
                Gap(Space.item)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.card),
                    horizontalArrangement = Arrangement.spacedBy(Space.inside),
                ) {
                    choices.forEach { category ->
                        SmallPill(category.name) { onSort(expense.id, category.id) }
                    }
                }
            }
        }
    }
}

/**
 * Categories in the order most likely to be tapped: most used this month first, then the everyday
 * ones (Food, Commute, Other), then the rest in budget order.
 */
private fun sortChoices(summary: MonthSummary): List<Category> {
    val used = summary.expenses.mapNotNull { it.categoryId }.groupingBy { it }.eachCount()
    val everyday = listOf("food", "commute", "other")
    return summary.categories
        .map { it.category }
        .filter { !it.archived }
        .sortedWith(
            compareByDescending<Category> { used[it.id] ?: 0 }
                .thenBy { everyday.indexOf(it.id).let { index -> if (index < 0) everyday.size else index } }
                .thenBy { it.position },
        )
}

@Composable
internal fun SmallPill(text: String, selected: Boolean = false, onClick: () -> Unit) {
    val colors = HabitaTheme.colors
    Box(
        Modifier
            .height(40.dp)
            .clip(ControlShape)
            .background(if (selected) colors.highlight else colors.background)
            .border(1.dp, if (selected) colors.highlight else colors.border, ControlShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        HText(text, HabitaTheme.typography.label, color = if (selected) colors.onHighlight else colors.text, maxLines = 1)
    }
}

/** Each category: spent against its limit, and what's left. */
@Composable
private fun ColumnScope.Categories(summary: MonthSummary, onBudget: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Overline("Budget", Modifier.weight(1f))
        TextAction("Edit", onBudget)
    }
    Gap(Space.tiny)
    HabitaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = Space.card, vertical = Space.inside)) {
        summary.categories.forEachIndexed { index, spend ->
            if (index > 0) Divider()
            CategoryRow(spend)
        }
    }
}

@Composable
private fun CategoryRow(spend: CategorySpend) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val remaining = spend.remainingPaise
    Column(Modifier.fillMaxWidth().padding(vertical = Space.item)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HText(spend.category.name, type.bodyStrong, modifier = Modifier.weight(1f), maxLines = 1)
            HText(
                when {
                    remaining == null -> formatRupees(spend.spentPaise)
                    remaining < 0 -> "${formatRupees(-remaining)} over"
                    else -> "${formatRupees(remaining)} left"
                },
                type.label,
                color = if (spend.isOver) colors.error else colors.text,
            )
        }
        if (remaining != null) {
            Gap(Space.tiny)
            HText("${formatRupees(spend.spentPaise)} of ${formatRupees(spend.category.limitPaise)}", type.caption, color = colors.muted)
            Gap(Space.inside)
            ProgressBar(spend.used.coerceAtMost(1f), color = if (spend.isOver) colors.error else colors.accent)
        }
    }
}

/** Every payment of the month, newest first, grouped by day. */
@Composable
private fun ColumnScope.Payments(summary: MonthSummary, onOpen: (String) -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val names = summary.categories.associate { it.category.id to it.category.name }
    Overline("Payments · ${summary.expenses.size}")
    Gap(Space.item)
    if (summary.expenses.isEmpty()) {
        HText("No payments yet this month.", type.body, color = colors.muted)
        return
    }
    summary.expenses.groupBy { startOfDay(it.paidAt) }.forEach { (day, payments) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            HText(dayLabel(day), type.caption, color = colors.muted, modifier = Modifier.weight(1f))
            HText(formatRupees(payments.sumOf { it.amountPaise }), type.caption, color = colors.muted)
        }
        Gap(Space.inside)
        HabitaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            payments.forEachIndexed { index, expense ->
                if (index > 0) Divider(Modifier.padding(start = Space.card))
                PaymentRow(expense, expense.categoryId?.let { names[it] }, onOpen)
            }
        }
        Gap(Space.item)
    }
}

@Composable
private fun PaymentRow(expense: Expense, categoryName: String?, onOpen: (String) -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpen(expense.id) }
            .padding(horizontal = Space.card, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            HText(expense.description, type.bodyStrong, maxLines = 1)
            val source = if (expense.source == ExpenseSource.SMS) " · SMS" else ""
            HText(
                "${categoryName ?: "Not sorted"} · ${timeLabel(expense.paidAt)} · ${expense.mode.label}$source",
                type.caption,
                color = if (categoryName == null) colors.accent else colors.muted,
                maxLines = 1,
            )
        }
        Gap(Space.item)
        HText(formatRupees(expense.amountPaise), type.bodyStrong)
    }
}