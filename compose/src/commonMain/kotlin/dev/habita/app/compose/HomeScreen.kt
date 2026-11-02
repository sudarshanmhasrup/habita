package dev.habita.app.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.ui.DashedCard
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HIcon
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.HabitaIcons
import dev.habita.app.core.ui.IconCircleButton
import dev.habita.app.core.ui.IconTile
import dev.habita.app.core.ui.Pill

/** The only screen: a greeting, the feature list and the theme toggle. */
@Composable
internal fun HomeScreen(
    userName: String,
    hour: Int,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenGym: () -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconCircleButton(
                icon = if (darkTheme) HabitaIcons.Sun else HabitaIcons.Moon,
                contentDescription = if (darkTheme) "Switch to light mode" else "Switch to dark mode",
                onClick = onToggleTheme,
            )
        }
        Gap(48.dp)
        HText(if (userName.isBlank()) "Hi there" else "Hi $userName", type.display)
        Gap(10.dp)
        HText("${partOfDay(hour)}, how's the day going?", type.body, color = colors.muted)
        Gap(36.dp)
        HabitaCard(Modifier.fillMaxWidth(), onClick = onOpenGym) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(HabitaIcons.Dumbbell, background = colors.accentTint, tint = colors.accent)
                Gap(16.dp)
                HText("Gym", type.title, modifier = Modifier.weight(1f))
                HIcon(HabitaIcons.ChevronRight, contentDescription = null, tint = colors.muted)
            }
        }
        Gap(16.dp)
        DashedCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(HabitaIcons.Wallet, background = colors.border.copy(alpha = 0.5f), tint = colors.muted)
                Gap(16.dp)
                HText("Expense tracker", type.title, color = colors.muted, modifier = Modifier.weight(1f))
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