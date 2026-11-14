package dev.habita.app.feature.expense.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.ui.CountingText
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HIcon
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaIcons
import dev.habita.app.feature.expense.data.formatRupees

/** What shows in place of a hidden amount: the rupee sign and four dots. */
internal const val HIDDEN_RUPEES = "₹••••"

/** [paise] as rupees, or [HIDDEN_RUPEES] while it is [hidden]. */
internal fun rupeesOrHidden(paise: Long, hidden: Boolean): String = if (hidden) HIDDEN_RUPEES else formatRupees(paise)

/**
 * The month's big amount-left number, hidden behind [HIDDEN_RUPEES] until the eye next to it is
 * tapped, and tapped again to hide. Kept in memory only: it starts hidden every time it is shown
 * and hides again whenever the app goes to the background, so opening the app never shows it.
 */
@Composable
fun AmountLeft(paise: Long, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    var hidden by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { hidden = true }
    val label = if (hidden) "Show amount" else "Hide amount"
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // The same style either way, so the card keeps its height when it toggles.
        if (hidden) {
            HText(rupeesOrHidden(paise, hidden = true), style, color = color, maxLines = 1)
        } else {
            CountingText(paise, { formatRupees(it) }, style, color = color)
        }
        Gap(4.dp)
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = label) { hidden = !hidden },
            contentAlignment = Alignment.Center,
        ) {
            HIcon(if (hidden) HabitaIcons.Eye else HabitaIcons.EyeOff, label, Modifier.size(20.dp), tint = HabitaTheme.colors.muted)
        }
    }
}