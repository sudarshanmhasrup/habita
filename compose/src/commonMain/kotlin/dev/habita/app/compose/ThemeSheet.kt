package dev.habita.app.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.Accent
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.Chip
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HIcon
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaCard
import dev.habita.app.core.ui.HabitaIcons
import dev.habita.app.core.ui.Motion
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PrimaryButton

/**
 * The theme: light or dark, and the accent colour. Each tap applies at once, so the screen behind
 * shows the result; the choice is saved and synced to every device.
 */
@Composable
internal fun BoxScope.ThemeSheet(
    visible: Boolean,
    darkTheme: Boolean,
    accent: Accent,
    onDarkTheme: (Boolean) -> Unit,
    onAccent: (Accent) -> Unit,
    onClose: () -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    AnimatedVisibility(visible, enter = fadeIn(tween(Motion.SHORT)), exit = fadeOut(tween(Motion.SHORT))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(interactionSource = null, indication = null, onClick = onClose),
        )
    }
    AnimatedVisibility(
        visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(Motion.MEDIUM, easing = Motion.Ease)) { it } + fadeIn(),
        exit = slideOutVertically(tween(Motion.SHORT)) { it } + fadeOut(),
    ) {
        HabitaCard(Modifier.fillMaxWidth().padding(Space.item)) {
            HText("Theme", type.title)
            Gap(2.dp)
            HText("Saved to your account, so every device matches.", type.caption, color = colors.muted)
            Gap(Space.card)
            Overline("Mode")
            Gap(Space.inside)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.inside)) {
                Chip("Light", selected = !darkTheme, onClick = { onDarkTheme(false) }, modifier = Modifier.weight(1f))
                Chip("Dark", selected = darkTheme, onClick = { onDarkTheme(true) }, modifier = Modifier.weight(1f))
            }
            Gap(Space.card)
            Overline("Colour")
            Gap(Space.item)
            Accent.entries.chunked(3).forEachIndexed { row, accents ->
                if (row > 0) Gap(Space.item)
                Row(Modifier.fillMaxWidth()) {
                    accents.forEach { option ->
                        Swatch(option, selected = option == accent, darkTheme = darkTheme, modifier = Modifier.weight(1f)) { onAccent(option) }
                    }
                }
            }
            Gap(Space.section)
            PrimaryButton("Done", onClose, Modifier.fillMaxWidth())
        }
    }
}

/** A colour dot with its name; the chosen one is ringed and checked. */
@Composable
private fun Swatch(accent: Accent, selected: Boolean, darkTheme: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    val scale by animateFloatAsState(if (selected) 1f else 0.86f, tween(Motion.SHORT), label = "swatch")
    val color = accent.swatch(darkTheme)
    Column(
        modifier
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = accent.label
                this.selected = selected
            }
            .padding(vertical = Space.tiny),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .border(2.dp, if (selected) color else Color.Transparent, CircleShape)
                .padding(5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    HIcon(
                        HabitaIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (darkTheme) colors.background else Color.White,
                    )
                }
            }
        }
        Gap(6.dp)
        HText(accent.label, type.caption, color = if (selected) colors.text else colors.muted, textAlign = TextAlign.Center)
    }
}