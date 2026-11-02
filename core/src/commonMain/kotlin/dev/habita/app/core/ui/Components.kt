package dev.habita.app.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaTheme

val CardShape = RoundedCornerShape(22.dp)
val ControlShape = RoundedCornerShape(16.dp)

/** Plain text in the theme's colors. */
@Composable
fun HText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = HabitaTheme.colors.text,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = textAlign ?: TextAlign.Unspecified),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun HIcon(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = HabitaTheme.colors.text) {
    Image(icon, contentDescription, modifier.size(24.dp), colorFilter = ColorFilter.tint(tint))
}

/**
 * A full screen: the background, a soft accent glow at the top, and padding that keeps content
 * clear of the system bars and the keyboard.
 */
@Composable
fun HabitaScreen(
    modifier: Modifier = Modifier,
    glow: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = HabitaTheme.colors
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .drawBehind {
                if (glow) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(colors.accentTint.copy(alpha = if (colors.isDark) 0.7f else 0.9f), Color.Transparent),
                            center = Offset(size.width * 0.7f, 0f),
                            radius = size.width * 0.75f,
                        ),
                        radius = size.width * 0.75f,
                        center = Offset(size.width * 0.7f, 0f),
                    )
                }
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        content = content,
    )
}

/** A white card with the standard radius and border. Clickable when [onClick] is set. */
@Composable
fun HabitaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = HabitaTheme.colors.card,
    borderColor: Color = HabitaTheme.colors.border,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(CardShape)
            .background(color)
            .border(1.dp, borderColor, CardShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** A dashed, unclickable card for something not built yet. */
@Composable
fun DashedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val border = HabitaTheme.colors.border
    Column(
        modifier
            .drawBehind {
                drawRoundRect(
                    color = border,
                    cornerRadius = CornerRadius(22.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
            .padding(20.dp),
        content = content,
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = HabitaTheme.colors
    ButtonBox(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.background(if (enabled) colors.accent else colors.accent.copy(alpha = 0.4f), ControlShape),
    ) {
        HText(text, HabitaTheme.typography.button, color = colors.onAccent)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = HabitaTheme.colors.text,
) {
    val colors = HabitaTheme.colors
    ButtonBox(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.border(1.dp, colors.border, ControlShape).background(colors.card, ControlShape),
    ) {
        HText(text, HabitaTheme.typography.button, color = if (enabled) color else colors.muted.copy(alpha = 0.6f))
    }
}

@Composable
private fun ButtonBox(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(ControlShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** A small text action, such as "Finish" in a top bar. */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = HabitaTheme.colors.accent) {
    Box(
        modifier
            .clip(ControlShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        HText(text, HabitaTheme.typography.label, color = color)
    }
}

/** "‹ Home" at the top left of a screen. */
@Composable
fun BackLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(ControlShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(top = 10.dp, bottom = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HIcon(HabitaIcons.ChevronLeft, contentDescription = null, modifier = Modifier.size(20.dp), tint = HabitaTheme.colors.muted)
        Spacer(Modifier.width(4.dp))
        HText(text, HabitaTheme.typography.bodyStrong, color = HabitaTheme.colors.muted)
    }
}

/** A round icon button, such as the theme toggle. */
@Composable
fun IconCircleButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HabitaTheme.colors
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colors.card)
            .border(1.dp, colors.border, CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        HIcon(icon, contentDescription, modifier = Modifier.size(22.dp))
    }
}

/** A rounded tile holding an icon, used at the start of feature cards. */
@Composable
fun IconTile(icon: ImageVector, background: Color, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(52.dp).background(background, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        HIcon(icon, contentDescription = null, tint = tint)
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, background: Color = HabitaTheme.colors.accentTint, color: Color = HabitaTheme.colors.accent) {
    Box(modifier.background(background, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp)) {
        HText(text, HabitaTheme.typography.caption, color = color)
    }
}

/** A 48 dp choice chip. [selected] chips are filled with the accent. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HabitaTheme.colors
    Box(
        modifier
            .height(48.dp)
            .clip(ControlShape)
            .background(if (selected) colors.accent else colors.card)
            .border(1.dp, if (selected) colors.accent else colors.border, ControlShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        HText(text, HabitaTheme.typography.button, color = if (selected) colors.onAccent else colors.text)
    }
}

/**
 * A labelled text field. The outline turns accent green while it has focus.
 */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
) {
    val colors = HabitaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier) {
        HText(label, HabitaTheme.typography.label)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = HabitaTheme.typography.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(colors.card, ControlShape)
                .border(if (focused) 2.dp else 1.dp, if (focused) colors.accent else colors.border, ControlShape),
            decorationBox = { inner ->
                Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) { inner() }
            },
        )
    }
}

/**
 * A number with − and + buttons either side. The number can also be typed. [unit] is shown after
 * the number, such as "kg".
 */
@Composable
fun Stepper(
    value: String,
    onValueChange: (String) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    contentDescription: String,
) {
    val colors = HabitaTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(colors.card, ControlShape)
            .border(1.dp, colors.border, ControlShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(HabitaIcons.Minus, "Less $contentDescription", onMinus)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                if (value.isEmpty()) {
                    HText(placeholder, HabitaTheme.typography.number, color = colors.muted.copy(alpha = 0.6f))
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = HabitaTheme.typography.number.copy(color = colors.text, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = keyboardOptions,
                    modifier = Modifier.width(110.dp),
                )
            }
            if (unit != null) {
                HText(unit, HabitaTheme.typography.bodyStrong, color = colors.muted)
            }
        }
        StepperButton(HabitaIcons.Plus, "More $contentDescription", onPlus)
    }
}

@Composable
private fun StepperButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .clip(ControlShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        HIcon(icon, label)
    }
}

/** A thin progress bar; [progress] runs from 0 to 1. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val colors = HabitaTheme.colors
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        val y = size.height / 2
        drawLine(colors.border, Offset(0f, y), Offset(size.width, y), size.height, StrokeCap.Round)
        val end = size.width * progress.coerceIn(0f, 1f)
        if (end > 0f) drawLine(colors.accent, Offset(0f, y), Offset(end, y), size.height, StrokeCap.Round)
    }
}

/** A muted label on the left and its value on the right. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        HText(label, HabitaTheme.typography.body, color = HabitaTheme.colors.muted)
        Spacer(Modifier.width(16.dp))
        HText(value, HabitaTheme.typography.bodyStrong, textAlign = TextAlign.End)
    }
}

/** Spacing between stacked items. */
@Composable
fun RowScope.Gap(width: Dp) = Spacer(Modifier.width(width))

@Composable
fun ColumnScope.Gap(height: Dp) = Spacer(Modifier.height(height))