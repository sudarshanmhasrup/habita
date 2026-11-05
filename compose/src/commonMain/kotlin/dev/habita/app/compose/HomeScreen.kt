package dev.habita.app.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.IconBadge
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HIcon
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.HabitaIcons
import dev.habita.app.core.ui.IconCircleButton
import dev.habita.app.core.ui.LocalWideLayout
import dev.habita.app.core.ui.TwoPane

/** The only screen: the date, a greeting, the feature list and the theme toggle. */
@Composable
internal fun HomeScreen(
    userName: String,
    hour: Int,
    darkTheme: Boolean,
    gymStatus: String?,
    expenseStatus: String?,
    onToggleTheme: () -> Unit,
    onOpenGym: () -> Unit,
    onOpenExpense: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val wide = LocalWideLayout.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = Space.screen, end = Space.screen, top = Space.item),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HText(todayLabel(), type.label, color = colors.muted, modifier = Modifier.weight(1f))
            IconCircleButton(
                icon = if (darkTheme) HabitaIcons.Sun else HabitaIcons.Moon,
                contentDescription = if (darkTheme) "Switch to light mode" else "Switch to dark mode",
                onClick = onToggleTheme,
            )
        }
        TwoPane(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Space.screen, vertical = if (wide) 48.dp else Space.section),
            first = {
                if (userName.isBlank()) {
                    HText(partOfDay(hour), type.display)
                } else {
                    HText(partOfDay(hour) + ",", type.body, color = colors.muted)
                    Gap(2.dp)
                    HText(userName, type.display)
                }
            },
            second = { Features(gymStatus, expenseStatus, onOpenGym, onOpenExpense) },
        )
        Box(Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.item)) { bottomBar() }
    }
}

/** The features, one tile each: the gym and the expense tracker. */
@Composable
private fun ColumnScope.Features(gymStatus: String?, expenseStatus: String?, onOpenGym: () -> Unit, onOpenExpense: () -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    HText("Your space", type.overline, color = colors.muted)
    Gap(Space.item)
    HabitaCard(Modifier.fillMaxWidth(), onClick = onOpenGym, contentPadding = PaddingValues(16.dp)) {
        FeatureRow(HabitaIcons.Dumbbell, "Gym", gymStatus) { OpenArrow() }
    }
    Gap(Space.inside)
    HabitaCard(Modifier.fillMaxWidth(), onClick = onOpenExpense, contentPadding = PaddingValues(16.dp)) {
        FeatureRow(HabitaIcons.Wallet, "Expenses", expenseStatus) { OpenArrow() }
    }
}

/** A small round arrow at the end of a feature tile. */
@Composable
private fun OpenArrow() {
    val colors = HabitaTheme.colors
    Box(Modifier.size(36.dp).background(colors.highlight, CircleShape), contentAlignment = Alignment.Center) {
        HIcon(HabitaIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.onHighlight)
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    detail: String?,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val tint = if (enabled) colors.text else colors.muted
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint = tint)
        Gap(Space.item)
        Column(Modifier.weight(1f)) {
            HText(title, type.title, color = tint, maxLines = 1)
            if (detail != null) HText(detail, type.caption, color = colors.muted, maxLines = 1)
        }
        Gap(Space.item)
        trailing()
    }
}

internal fun partOfDay(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}