package dev.habita.app.compose

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
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.DashedCard
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HIcon
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.HabitaIcons
import dev.habita.app.core.ui.IconCircleButton
import dev.habita.app.core.ui.IconTile
import dev.habita.app.core.ui.LocalWideLayout
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.Pill
import dev.habita.app.core.ui.TwoPane

/** The only screen: the date, a greeting, the feature list and the theme toggle. */
@Composable
internal fun HomeScreen(
    userName: String,
    hour: Int,
    darkTheme: Boolean,
    gymStatus: String?,
    onToggleTheme: () -> Unit,
    onOpenGym: () -> Unit,
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
            second = { Features(gymStatus, onOpenGym) },
        )
    }
}

/** The feature cards: the gym, and what is coming next. */
@Composable
private fun ColumnScope.Features(gymStatus: String?, onOpenGym: () -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    Overline("Your space")
    Gap(Space.item)
    HabitaCard(Modifier.fillMaxWidth(), onClick = onOpenGym) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(HabitaIcons.Dumbbell, background = colors.accentTint, tint = colors.accent)
            Gap(Space.card)
            Column(Modifier.weight(1f)) {
                HText("Gym", type.title)
                if (gymStatus != null) HText(gymStatus, type.caption, color = colors.muted, maxLines = 1)
            }
            HIcon(HabitaIcons.ChevronRight, contentDescription = null, tint = colors.muted)
        }
    }
    Gap(Space.item)
    DashedCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(HabitaIcons.Wallet, background = colors.border.copy(alpha = 0.5f), tint = colors.muted)
            Gap(Space.card)
            Column(Modifier.weight(1f)) {
                HText("Expense tracker", type.title, color = colors.muted, maxLines = 1)
                Gap(Space.tiny)
                Pill("Coming soon", background = colors.border.copy(alpha = 0.6f), color = colors.muted)
            }
        }
    }
}

internal fun partOfDay(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}