package dev.habita.app.feature.gym.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.Motion
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.Scrim
import dev.habita.app.core.ui.SecondaryButton

/**
 * Asks before something that can't be taken back, over a dimmed screen. Tapping outside
 * ([onDismiss], [onCancel] unless given) or [cancel] backs out. A [destructive] action is drawn in the error colour and the safe choice
 * becomes the main button, so a quick tap never deletes anything.
 */
@Composable
internal fun BoxScope.ConfirmPanel(
    visible: Boolean,
    title: String,
    detail: String,
    confirm: String,
    onConfirm: () -> Unit,
    cancel: String,
    onCancel: () -> Unit,
    destructive: Boolean = false,
    onDismiss: () -> Unit = onCancel,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    Scrim(visible, onDismiss = onDismiss)
    AnimatedVisibility(
        visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(Motion.MEDIUM, easing = Motion.Ease)) { it } + fadeIn(),
        exit = slideOutVertically(tween(Motion.SHORT)) { it } + fadeOut(),
    ) {
        HabitaCard(Modifier.fillMaxWidth().padding(Space.item)) {
            HText(title, type.title)
            Gap(Space.tiny)
            HText(detail, type.body, color = colors.muted)
            Gap(Space.card)
            if (destructive) {
                PrimaryButton(cancel, onCancel, Modifier.fillMaxWidth())
                Gap(Space.inside)
                SecondaryButton(confirm, onConfirm, Modifier.fillMaxWidth(), color = colors.error)
            } else {
                PrimaryButton(confirm, onConfirm, Modifier.fillMaxWidth())
                Gap(Space.inside)
                SecondaryButton(cancel, onCancel, Modifier.fillMaxWidth())
            }
        }
    }
}