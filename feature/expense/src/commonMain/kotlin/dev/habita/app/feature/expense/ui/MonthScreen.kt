package dev.habita.app.feature.expense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import dev.habita.app.core.data.nowMillis
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
import dev.habita.app.core.ui.appear
import dev.habita.app.core.ui.CountingText
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
    thisMonth: YearMonth,
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
                MonthSwitcher(month, summary?.rangeLabel, canGoBack, canGoForward, onPrevious, onNext)
                Gap(Space.item)
                if (month > thisMonth) {
                    // Next month, open early to plan its budget.
                    HabitaCard(Modifier.fillMaxWidth(), borderColor = HabitaTheme.colors.accent) {
                        HText("Next month", type.title)
                        Gap(Space.tiny)
                        HText("It hasn't started yet. Plan its budget now; this month keeps its own.", type.caption, color = HabitaTheme.colors.muted)
                        Gap(Space.item)
                        PrimaryButton("Plan the budget", onBudget, Modifier.fillMaxWidth())
                    }
                    Gap(Space.item)
                }
                if (summary != null) Overview(summary, onBudget, Modifier.appear(0))
                if (sms.supported && month == thisMonth) {
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
                    // The budget first, so where the month stands is the first thing seen.
                    Categories(summary, onBudget)
                    if (summary.spentPaise > 0) {
                        Gap(Space.section)
                        WhereItWent(summary)
                    }
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
private fun MonthSwitcher(month: YearMonth, range: String?, canGoBack: Boolean, canGoForward: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundArrow(HabitaIcons.ChevronLeft, "Previous month", canGoBack, onPrevious)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            HText(monthLabel(month), HabitaTheme.typography.bodyStrong, textAlign = TextAlign.Center)
            // A month from salary to salary says which days it covers.
            if (range != null) HText(range, HabitaTheme.typography.caption, color = HabitaTheme.colors.muted, textAlign = TextAlign.Center)
        }
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

/**
 * What's left of the income and how much went out. Without an income set, only what was spent,
 * with a way to set the income, since "left" would mean nothing.
 */
@Composable
private fun Overview(summary: MonthSummary, onBudget: () -> Unit, modifier: Modifier) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val big = type.timer.copy(fontSize = type.display.fontSize * 1.2f, lineHeight = type.display.lineHeight * 1.15f)
    HabitaCard(modifier.fillMaxWidth()) {
        if (summary.incomePaise <= 0) {
            Overline("Spent this month")
            Gap(Space.tiny)
            CountingText(summary.spentPaise, { formatRupees(it) }, big)
            Gap(Space.inside)
            Row(verticalAlignment = Alignment.CenterVertically) {
                HText("Add your income to see what's left.", type.caption, color = colors.muted, modifier = Modifier.weight(1f))
                TextAction("Set income", onBudget)
            }
            return@HabitaCard
        }
        val over = summary.leftPaise < 0
        Overline(if (over) "Over your income" else "Left from income")
        Gap(Space.tiny)
        AmountLeft(if (over) -summary.leftPaise else summary.leftPaise, big, color = if (over) colors.error else colors.text)
        Gap(Space.tiny)
        HText(
            "Spent ${formatRupees(summary.spentPaise)} of ${formatRupees(summary.incomePaise)}",
            type.body,
            color = colors.muted,
        )
        Gap(Space.item)
        ProgressBar(
            summary.spentPaise.toFloat() / summary.incomePaise,
            height = 6.dp,
            color = if (over) colors.error else colors.accent,
        )
    }
}

/** Asks for SMS access, or offers to catch up on this month's and last month's bank SMS. */
@Composable
private fun ColumnScope.SmsCard(sms: SmsAccess, onImport: () -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    if (!sms.granted) {
        HabitaCard(Modifier.fillMaxWidth().appear(1), contentPadding = PaddingValues(start = Space.card, end = Space.inside, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    HText("Log payments from bank SMS", type.bodyStrong)
                    HText("SMS never leave the phone.", type.caption, color = colors.muted)
                }
                TextAction("Turn on", sms::request)
            }
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
            TextAction("Scan recent SMS", onImport)
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
            HabitaCard(Modifier.fillMaxWidth().appear(2), onClick = { onOpen(expense.id) }, contentPadding = PaddingValues(vertical = Space.card)) {
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

/**
 * Each category as a table row: its limit, spent and what's left, with a bar of how much of the limit
 * is used. In the month running now, a tick on each bar marks how far through the month it is, so
 * a bar past its tick is going faster than the month.
 */
@Composable
private fun ColumnScope.Categories(summary: MonthSummary, onBudget: () -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    Row(verticalAlignment = Alignment.CenterVertically) {
        Overline("Budget", Modifier.weight(1f))
        TextAction("Edit", onBudget)
    }
    Gap(Space.tiny)
    // Only categories with a limit or some spending; the rest are in the budget editor.
    val shown = summary.categories.filter { it.category.limitPaise > 0 || it.spentPaise > 0 }
    if (shown.isEmpty()) {
        HText("Nothing spent yet. Set limits under Edit.", type.caption, color = colors.muted)
        return
    }
    val day = summary.dayOf(nowMillis())
    val pace = day?.let { it.toFloat() / summary.dayCount }
    HabitaCard(Modifier.fillMaxWidth().appear(3), contentPadding = PaddingValues(horizontal = Space.card, vertical = Space.card)) {
        if (summary.plannedPaise > 0) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    HText("Planned", type.caption, color = colors.muted)
                    HText(formatRupees(summary.plannedPaise), type.title)
                }
                if (day != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        HText("Day $day of ${summary.dayCount}", type.label)
                        HText("Tick = today", type.caption, color = colors.muted)
                    }
                }
            }
            Gap(Space.item)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TableHead("Category", Modifier.weight(1f), TextAlign.Start)
            TableHead("Spent", Modifier.width(AMOUNT_WIDTH))
            TableHead("Left", Modifier.width(AMOUNT_WIDTH))
        }
        Gap(Space.inside)
        Divider()
        shown.forEachIndexed { index, spend ->
            if (index > 0) Divider()
            CategoryRow(spend, pace)
        }
    }
}

private val AMOUNT_WIDTH = 76.dp

@Composable
private fun TableHead(text: String, modifier: Modifier, align: TextAlign = TextAlign.End) {
    HText(text.uppercase(), HabitaTheme.typography.overline, color = HabitaTheme.colors.muted, textAlign = align, modifier = modifier, maxLines = 1)
}

@Composable
private fun CategoryRow(spend: CategorySpend, pace: Float?) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val remaining = spend.remainingPaise
    Column(Modifier.fillMaxWidth().padding(vertical = Space.item)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                HText(spend.category.name, type.bodyStrong, maxLines = 2)
                HText(if (remaining == null) "No limit" else "of ${formatRupees(spend.category.limitPaise)}", type.caption, color = colors.muted, maxLines = 1)
            }
            Amount(formatRupees(spend.spentPaise), colors.text)
            Amount(
                when {
                    remaining == null -> "—"
                    remaining < 0 -> "−" + formatRupees(-remaining)
                    else -> formatRupees(remaining)
                },
                if (spend.isOver) colors.error else colors.text,
            )
        }
        if (remaining != null) {
            Gap(Space.inside)
            PaceBar(spend.used, pace, if (spend.isOver) colors.error else colors.accent)
        }
    }
}

@Composable
private fun Amount(text: String, color: Color) {
    HText(text, HabitaTheme.typography.label, color = color, textAlign = TextAlign.End, modifier = Modifier.width(AMOUNT_WIDTH), maxLines = 1)
}

/** A bar of [used] (0 to 1, more is full), with a tick at [pace] when it's given. */
@Composable
private fun PaceBar(used: Float, pace: Float?, color: Color) {
    val colors = HabitaTheme.colors
    Box(Modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.Center) {
        ProgressBar(used.coerceAtMost(1f), color = color, height = 6.dp)
        if (pace != null) {
            Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                val x = size.width * pace.coerceIn(0f, 1f)
                drawLine(colors.text, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

/**
 * Where the month's money went: one bar split by the biggest categories, then a row each with its
 * amount and share, so nothing is read from colour alone.
 */
@Composable
private fun ColumnScope.WhereItWent(summary: MonthSummary) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val spends = summary.biggestSpends()
    // Shades of the accent, darkest for the biggest; everything else in the border colour.
    val shades = listOf(1f, 0.7f, 0.45f, 0.25f)
    val parts = spends.mapIndexed { index, (category, paise) ->
        Triple(category?.name ?: "Everything else", paise, if (category == null) colors.border else colors.accent.copy(alpha = shades[index.coerceAtMost(shades.lastIndex)]))
    }
    Overline("Where it went")
    Gap(Space.item)
    HabitaCard(Modifier.fillMaxWidth().appear(3), contentPadding = PaddingValues(Space.card)) {
        Canvas(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp))) {
            val gap = 2.dp.toPx()
            var x = 0f
            parts.forEach { (_, paise, color) ->
                val width = size.width * paise / summary.spentPaise
                drawRect(color, Offset(x, 0f), Size((width - gap).coerceAtLeast(1f), size.height))
                x += width
            }
        }
        Gap(Space.item)
        parts.forEach { (name, paise, color) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Gap(Space.inside)
                HText(name, type.body, modifier = Modifier.weight(1f), maxLines = 1)
                HText("${percent(paise, summary.spentPaise)}%", type.caption, color = colors.muted)
                Gap(Space.item)
                HText(formatRupees(paise), type.bodyStrong, textAlign = TextAlign.End, modifier = Modifier.width(AMOUNT_WIDTH + 16.dp))
            }
        }
    }
}

/** [part] as a whole percentage of [whole]. */
internal fun percent(part: Long, whole: Long): Long = if (whole <= 0) 0 else (part * 100 + whole / 2) / whole

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
        HabitaCard(Modifier.fillMaxWidth().appear(2), contentPadding = PaddingValues(0.dp)) {
            payments.forEachIndexed { index, expense ->
                if (index > 0) Divider(Modifier.padding(start = Space.card))
                PaymentRow(expense, splitLabel(expense, names) ?: expense.categoryId?.let { names[it] }, onOpen)
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

/** "Food + Household" for a split payment, `null` for one in a single category. */
internal fun splitLabel(expense: Expense, names: Map<String, String>): String? =
    if (expense.isSplit) expense.parts.map { names[it.categoryId] ?: "Other" }.distinct().joinToString(" + ") else null