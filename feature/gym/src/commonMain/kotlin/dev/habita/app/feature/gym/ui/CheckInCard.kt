package dev.habita.app.feature.gym.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.appear
import dev.habita.app.feature.gym.data.CheckInStatus
import dev.habita.app.shared.gym.CheckInReason

/**
 * Asks for a plan review when one is due: every three months, or when exercises stop improving.
 * The review happens in an AI chat, so the card only copies the data and the plan for it.
 */
@Composable
internal fun CheckInCard(status: CheckInStatus, onCopy: () -> Unit, message: String?, modifier: Modifier = Modifier) {
    val reason = status.due ?: return
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val (title, detail) = when (reason) {
        CheckInReason.SCHEDULED -> "Time to review the plan" to
            "About three months of training to look at. Copy it for Claude or ChatGPT and ask what to change."
        CheckInReason.PLATEAU -> stuckTitle(status.stuck) to
            "No new best in 6 weeks. Copy your data for Claude or ChatGPT for an outside look."
    }
    HabitaCard(modifier.fillMaxWidth().appear(2), borderColor = colors.accent) {
        Overline("Check-in", color = colors.accent)
        Gap(Space.tiny)
        HText(title, type.title)
        Gap(Space.tiny)
        HText(detail, type.caption, color = colors.muted)
        if (reason == CheckInReason.PLATEAU && status.stuck.size > 1) {
            Gap(Space.inside)
            HText(status.stuck.joinToString(", "), type.caption)
        }
        Gap(Space.card)
        PrimaryButton("Copy for AI", onCopy, Modifier.fillMaxWidth())
        if (message != null) {
            Gap(Space.inside)
            HText(message, type.caption, color = colors.muted)
        }
    }
}

private fun stuckTitle(stuck: List<String>): String = when (stuck.size) {
    1 -> "${stuck.single()} is stuck"
    else -> "${stuck.size} exercises are stuck"
}