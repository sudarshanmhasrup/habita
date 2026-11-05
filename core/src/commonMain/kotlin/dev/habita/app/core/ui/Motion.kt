package dev.habita.app.core.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaTheme
import kotlinx.coroutines.delay

/**
 * The app's motion, in one place so every screen moves the same way: quick and soft, never
 * bouncy. Screens slide in the direction you travel, content eases up into place, pressed
 * controls sink a little, and numbers count to their value.
 */
object Motion {
    /** Material's "emphasized decelerate": fast start, gentle landing. */
    val Ease = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    const val SHORT = 180
    const val MEDIUM = 320
    const val LONG = 520

    /** How much later each item in a list starts appearing than the one before. */
    const val STAGGER = 45
}

/** Shrinks slightly while [interactionSource] is pressed, so a tap feels physical. */
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Fades the element in and lifts it into place when it first appears. Give items of a list
 * increasing [index]es so they arrive one after another.
 */
fun Modifier.appear(index: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(10) * Motion.STAGGER).toLong())
        progress.animateTo(1f, tween(Motion.LONG, easing = Motion.Ease))
    }
    val lift = 16.dp
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * lift.toPx()
    }
}

/**
 * Switches between screens: forward slides the new one in from the right, back from the left,
 * with a fade. [isForward] decides the direction from the old and new state.
 */
@Composable
fun <S> ScreenTransition(
    target: S,
    isForward: (from: S, to: S) -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (S) -> Unit,
) {
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = { screenTransform(isForward(initialState, targetState)) },
        contentAlignment = Alignment.TopCenter,
        label = "screen",
    ) { content(it) }
}

private fun <S> AnimatedContentTransitionScope<S>.screenTransform(forward: Boolean): ContentTransform {
    val sign = if (forward) 1 else -1
    val enter = slideInHorizontally(tween(Motion.MEDIUM, easing = Motion.Ease)) { it / 6 * sign } +
        fadeIn(tween(Motion.MEDIUM, easing = Motion.Ease))
    val exit = slideOutHorizontally(tween(Motion.SHORT)) { -it / 10 * sign } + fadeOut(tween(Motion.SHORT))
    return (enter togetherWith exit).using(SizeTransform(clip = false))
}

/**
 * A gentler switch for content inside a screen, such as a set changing to a rest. Only a change
 * of [key] animates; other changes to [target] update in place.
 */
@Composable
fun <S> ContentSwap(target: S, modifier: Modifier = Modifier, key: (S) -> Any? = { it }, content: @Composable (S) -> Unit) {
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        contentKey = key,
        transitionSpec = {
            (fadeIn(tween(Motion.MEDIUM, easing = Motion.Ease)) + scaleIn(tween(Motion.MEDIUM, easing = Motion.Ease), initialScale = 0.98f))
                .togetherWith(fadeOut(tween(Motion.SHORT)))
        },
        contentAlignment = Alignment.TopCenter,
        label = "content",
    ) { content(it) }
}

/**
 * A number that counts to [value] when it first shows and whenever it changes. [format] turns
 * the running value into text, for example with a currency sign.
 */
@Composable
fun CountingText(
    value: Long,
    format: (Long) -> String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = HabitaTheme.colors.text,
) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(value) {
        shown.animateTo(value.toFloat(), tween(Motion.LONG + 200, easing = Motion.Ease))
    }
    HText(format(shown.value.toLong()), style, modifier, color = color, maxLines = 1)
}

/**
 * Dims everything behind a panel and closes it on a tap. Screens are padded clear of the system
 * bars and kept narrow on tablets, so the dimming is drawn well past this box's edges; the window
 * cuts it off at the screen, which then dims evenly, bars and margins included.
 */
@Composable
fun BoxScope.Scrim(visible: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible,
        modifier = Modifier.matchParentSize(),
        enter = fadeIn(tween(Motion.SHORT)),
        exit = fadeOut(tween(Motion.SHORT)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val reach = 10_000f
                    drawRect(
                        Color.Black.copy(alpha = 0.32f),
                        topLeft = Offset(-reach, -reach),
                        size = Size(size.width + 2 * reach, size.height + 2 * reach),
                    )
                }
                .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        )
    }
}