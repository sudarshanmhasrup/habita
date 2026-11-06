package dev.habita.app.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.Accent
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.update.UpdateState
import dev.habita.app.core.ui.CountingText
import dev.habita.app.core.ui.Divider
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.HabitaIcons
import dev.habita.app.core.ui.IconCircleButton
import dev.habita.app.core.ui.LocalWideLayout
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.ProgressBar
import dev.habita.app.core.ui.SecondaryButton
import dev.habita.app.core.ui.TwoPane
import dev.habita.app.core.ui.appear
import dev.habita.app.feature.expense.ExpenseToday
import dev.habita.app.feature.expense.data.formatRupees
import dev.habita.app.feature.gym.GymToday
import dev.habita.app.feature.routine.RoutineToday

/**
 * The only screen: the date, a greeting and today at a glance. The routine card ticks the next
 * task of the day, the gym card starts today's workout in one tap, and the expense card shows
 * what's left this month and adds a payment.
 */
@Composable
internal fun HomeScreen(
    userName: String,
    hour: Int,
    darkTheme: Boolean,
    accent: Accent,
    gym: GymToday?,
    expense: ExpenseToday?,
    routine: RoutineToday?,
    update: UpdateState,
    onInstallUpdate: () -> Unit,
    onDarkTheme: (Boolean) -> Unit,
    onAccent: (Accent) -> Unit,
    onStartWorkout: (workoutId: String) -> Unit,
    onOpenGym: () -> Unit,
    onOpenRoutine: () -> Unit,
    onRoutineDone: (taskId: String) -> Unit,
    onOpenExpense: () -> Unit,
    onAddExpense: () -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val wide = LocalWideLayout.current
    var themeOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = Space.screen, end = Space.screen, top = Space.item),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Overline(todayLabel(), Modifier.weight(1f))
            IconCircleButton(
                icon = HabitaIcons.Palette,
                contentDescription = "Theme",
                onClick = { themeOpen = true },
            )
        }
        TwoPane(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Space.screen, vertical = if (wide) 48.dp else Space.card),
            first = {
                Column(Modifier.appear(0)) {
                    HText(if (userName.isBlank()) partOfDay(hour) else partOfDay(hour) + ",", type.body, color = colors.muted)
                    if (userName.isNotBlank()) {
                        Gap(2.dp)
                        HText(userName, type.display)
                    }
                }
                Gap(Space.section)
            },
            second = {
                if (update != UpdateState.None) {
                    UpdateCard(update, onInstallUpdate, Modifier.appear(0))
                    Gap(Space.item)
                }
                if (routine != null) {
                    RoutineCard(routine, onOpenRoutine, onRoutineDone, Modifier.appear(1))
                    Gap(Space.item)
                }
                if (gym != null) {
                    GymCard(gym, onStartWorkout, onOpenGym, Modifier.appear(2))
                    Gap(Space.item)
                }
                if (expense != null) {
                    ExpenseCard(expense, onOpenExpense, onAddExpense, Modifier.appear(3))
                }
                Gap(Space.card)
            },
        )
    }
    ThemeSheet(
        visible = themeOpen,
        darkTheme = darkTheme,
        accent = accent,
        onDarkTheme = onDarkTheme,
        onAccent = onAccent,
        onClose = { themeOpen = false },
    )
    }
}

/** A newer build on the server: install it, follow the download, or see why it failed. */
@Composable
private fun UpdateCard(update: UpdateState, onInstall: () -> Unit, modifier: Modifier) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val release = when (update) {
        is UpdateState.Available -> update.release
        is UpdateState.Downloading -> update.release
        is UpdateState.Installing -> update.release
        is UpdateState.Failed -> update.release
        UpdateState.None -> return
    }
    HabitaCard(modifier.fillMaxWidth(), borderColor = colors.accent) {
        Overline("Update", color = colors.accent)
        Gap(Space.tiny)
        HText("Habita ${release.versionName} is ready", type.title)
        Gap(2.dp)
        HText(
            when (update) {
                is UpdateState.Downloading -> "Downloading… ${(update.progress * 100).toInt()}%"
                is UpdateState.Installing -> "Confirm the install when Android asks."
                is UpdateState.Failed -> update.message
                else -> "${release.sizeBytes / (1024 * 1024)} MB · your data stays as it is."
            },
            type.caption,
            color = if (update is UpdateState.Failed) colors.error else colors.muted,
        )
        Gap(Space.item)
        when (update) {
            is UpdateState.Downloading -> ProgressBar(update.progress, height = 6.dp)
            is UpdateState.Installing -> Unit
            else -> PrimaryButton(if (update is UpdateState.Failed) "Try again" else "Install", onInstall, Modifier.fillMaxWidth())
        }
    }
}

/** Today's routine: how much is done, the next task, and a button that ticks it. */
@Composable
private fun RoutineCard(routine: RoutineToday, onOpen: () -> Unit, onDone: (String) -> Unit, modifier: Modifier) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    HabitaCard(modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline(routine.nextGroup?.let { "Routine · $it" } ?: "Routine", Modifier.weight(1f), color = colors.accent)
            HText("${routine.done} of ${routine.total}", type.caption, color = colors.muted)
        }
        Gap(Space.inside)
        HText(routine.nextTask ?: "All done for today", type.headline)
        Gap(2.dp)
        HText(if (routine.nextTask != null) "Up next" else "It starts again at midnight.", type.body, color = colors.muted)
        Gap(Space.item)
        ProgressBar(if (routine.total > 0) routine.done.toFloat() / routine.total else 0f, height = 6.dp)
        Gap(Space.card)
        val next = routine.nextTaskId
        if (next != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.inside)) {
                SecondaryButton("See all", onOpen, Modifier.weight(1f))
                PrimaryButton("Done", { onDone(next) }, Modifier.weight(1f))
            }
        } else {
            SecondaryButton("See the list", onOpen, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun GymCard(gym: GymToday, onStart: (String) -> Unit, onOpen: () -> Unit, modifier: Modifier) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    HabitaCard(modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline(if (gym.inProgress) "Gym · in progress" else "Gym · up next", Modifier.weight(1f), color = colors.accent)
            HText(weekLine(gym.workoutsThisWeek), type.caption, color = colors.muted)
        }
        Gap(Space.inside)
        HText(gym.name, type.headline)
        Gap(2.dp)
        HText(gym.detail, type.body, color = colors.muted)
        Gap(Space.card)
        WeekDots(gym.trainedDays, gym.todayIndex)
        Gap(Space.card)
        PrimaryButton(if (gym.inProgress) "Resume workout" else "Start workout", { onStart(gym.workoutId) }, Modifier.fillMaxWidth())
    }
}

/** Monday to Sunday as dots: filled for a day with a workout, ringed for today. */
@Composable
private fun WeekDots(trained: Set<Int>, today: Int) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, letter ->
            val done = index in trained
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics(mergeDescendants = true) { if (done) contentDescription = "Worked out" },
            ) {
                HText(letter, type.caption, color = if (index == today) colors.text else colors.muted)
                Gap(6.dp)
                Box(
                    Modifier
                        .size(28.dp)
                        .background(if (done) colors.accent else colors.accentTint.copy(alpha = if (colors.isDark) 0.6f else 0.55f), CircleShape)
                        .then(if (index == today && !done) Modifier.border(1.5.dp, colors.accent, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) HText("✓", type.caption, color = colors.onAccent, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun ExpenseCard(expense: ExpenseToday, onOpen: () -> Unit, onAdd: () -> Unit, modifier: Modifier) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    HabitaCard(modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline("${expense.monthName} · expenses", Modifier.weight(1f), color = colors.accent)
            if (expense.toSort > 0) {
                HText(if (expense.toSort == 1) "1 to sort" else "${expense.toSort} to sort", type.caption, color = colors.accent)
            }
        }
        Gap(Space.inside)
        if (expense.incomeSet) {
            val over = expense.leftPaise < 0
            CountingText(
                value = if (over) -expense.leftPaise else expense.leftPaise,
                format = { formatRupees(it) },
                style = type.number,
                color = if (over) colors.error else colors.text,
            )
            HText(
                if (over) "over this month's income" else "left of ${formatRupees(expense.incomePaise)}",
                type.body,
                color = colors.muted,
            )
            Gap(Space.item)
            ProgressBar(
                progress = if (expense.incomePaise > 0) expense.spentPaise.toFloat() / expense.incomePaise else 0f,
                height = 6.dp,
                color = if (over) colors.error else colors.accent,
            )
        } else {
            CountingText(value = expense.spentPaise, format = { formatRupees(it) }, style = type.number)
            HText("spent this month · set an income to see what's left", type.body, color = colors.muted)
        }
        if (expense.recent.isNotEmpty()) {
            Gap(Space.card)
            expense.recent.forEachIndexed { index, payment ->
                if (index > 0) Divider(Modifier.padding(vertical = 10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        HText(payment.description, type.bodyStrong, maxLines = 1)
                        HText(payment.category, type.caption, color = colors.muted, maxLines = 1)
                    }
                    HText(formatRupees(payment.amountPaise), type.bodyStrong)
                }
            }
        }
        Gap(Space.card)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.inside)) {
            SecondaryButton("See month", onOpen, Modifier.weight(1f))
            PrimaryButton("Add expense", onAdd, Modifier.weight(1f))
        }
    }
}

private fun weekLine(count: Int): String = when (count) {
    0 -> "None yet this week"
    1 -> "1 this week"
    else -> "$count this week"
}

internal fun partOfDay(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}