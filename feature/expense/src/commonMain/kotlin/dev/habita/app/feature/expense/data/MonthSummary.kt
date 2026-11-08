package dev.habita.app.feature.expense.data

/** How much went to one category in a month, against its limit. */
data class CategorySpend(val category: Category, val spentPaise: Long) {
    /** What's left of the limit (negative when over), or `null` for a category with no limit. */
    val remainingPaise: Long? = category.limitPaise.takeIf { it > 0 }?.let { it - spentPaise }

    /** Spent as a share of the limit, 0 when there is no limit. Can go past 1. */
    val used: Float = if (category.limitPaise > 0) spentPaise.toFloat() / category.limitPaise else 0f

    val isOver: Boolean = remainingPaise != null && remainingPaise < 0
}

/** A month of spending: totals, each category, and payments still to sort. */
data class MonthSummary(
    val month: YearMonth,
    val incomePaise: Long,
    val expenses: List<Expense>,
    val categories: List<CategorySpend>,
    /** The salary day the month starts on; 1 for a calendar month. */
    val payDay: Int = 1,
) {
    /** When the month starts and when the next one does. */
    val startsAt: Long get() = periodStart(month, payDay)
    val endsAt: Long get() = periodStart(month.next(), payDay)

    /** "5 Nov – 4 Dec" for a month from salary to salary, `null` for a calendar month. */
    val rangeLabel: String? get() = if (payDay <= 1) null else "${shortDayLabel(startsAt)} – ${shortDayLabel(endsAt - 1)}"

    val spentPaise: Long = expenses.sumOf { it.amountPaise }

    /** Income minus everything spent. */
    val leftPaise: Long = incomePaise - spentPaise

    /** The sum of all limits: what the month was planned to cost. */
    val plannedPaise: Long = categories.sumOf { it.category.limitPaise }

    /** Payments not sorted yet. A split payment counts as sorted. */
    val unsorted: List<Expense> = expenses.filter { !it.isSplit && it.categoryId == null }

    companion object {
        /**
         * Adds up [expenses] by category, a split payment part by part. Archived categories only
         * show when something was spent in them; payments in a category that no longer exists
         * count as unsorted.
         */
        fun of(month: YearMonth, incomePaise: Long, categories: List<Category>, expenses: List<Expense>, payDay: Int = 1): MonthSummary {
            val known = categories.associateBy { it.id }
            val cleaned = expenses.map {
                if (!it.isSplit && it.categoryId != null && it.categoryId !in known) it.copy(categoryId = null) else it
            }
            val spends = categories
                .map { category -> CategorySpend(category, cleaned.sumOf { expense -> expense.byCategory[category.id] ?: 0L }) }
                .filter { !it.category.archived || it.spentPaise > 0 }
            return MonthSummary(month, incomePaise, cleaned, spends, payDay)
        }
    }
}

/**
 * The month as markdown in the same layout as the user's Notion budget page: income, the limits,
 * what's left in each, and every payment. Meant for pasting into Notion or handing to an AI for a
 * closer look at where the money went.
 */
fun MonthSummary.toMarkdown(): String = buildString {
    // A "|" would split a table cell.
    val names = categories.associate { it.category.id to it.category.name.replace("|", "/") }
    appendLine("# ${monthLabel(month)}")
    appendLine()
    rangeLabel?.let {
        appendLine("_${it}_")
        appendLine()
    }
    appendLine("## Income")
    appendLine()
    appendLine("- **Salary:** ${formatRupees(incomePaise, symbol = false)}")
    appendLine()
    appendLine("## Expenses")
    appendLine()
    categories.filter { it.category.limitPaise > 0 }.forEachIndexed { index, spend ->
        appendLine("${index + 1}. **${spend.category.name}:** ${formatRupees(spend.category.limitPaise, symbol = false)}")
    }
    appendLine()
    appendLine("## Remaining")
    appendLine()
    categories.filter { it.category.limitPaise > 0 }.forEachIndexed { index, spend ->
        appendLine("${index + 1}. **${spend.category.name}:** ${formatRupees(spend.remainingPaise ?: 0, symbol = false)}")
    }
    appendLine()
    appendLine("## Spent")
    appendLine()
    appendLine("- **Total:** ${formatRupees(spentPaise, symbol = false)}")
    appendLine("- **Left from income:** ${formatRupees(leftPaise, symbol = false)}")
    categories.filter { it.spentPaise > 0 }.sortedByDescending { it.spentPaise }.forEach { spend ->
        appendLine("- **${spend.category.name}:** ${formatRupees(spend.spentPaise, symbol = false)}")
    }
    if (unsorted.isNotEmpty()) appendLine("- **Not sorted:** ${formatRupees(unsorted.sumOf { it.amountPaise }, symbol = false)}")
    appendLine()
    appendLine("## Transactions")
    appendLine()
    appendLine("| # | Date | Amount Paid | Description | Category | Payment Mode |")
    appendLine("| --- | --- | --- | --- | --- | --- |")
    expenses.sortedBy { it.paidAt }.forEachIndexed { index, expense ->
        val description = expense.description.replace("|", "/")
        val category = if (expense.isSplit) {
            expense.parts.joinToString(" + ") { part -> "${names[part.categoryId] ?: "Other"} ${formatRupees(part.amountPaise, symbol = false)}" }
        } else {
            expense.categoryId?.let { names[it] } ?: "Not sorted"
        }
        append("| ${index + 1} | ${shortDayLabel(expense.paidAt)} | ${formatRupees(expense.amountPaise, symbol = false)} ")
        appendLine("| $description | $category | ${expense.mode.label} |")
    }
}.trimEnd()