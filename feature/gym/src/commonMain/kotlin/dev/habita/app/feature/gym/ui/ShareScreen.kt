package dev.habita.app.feature.gym.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.theme.Space
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.Overline
import dev.habita.app.core.ui.PrimaryButton
import dev.habita.app.core.ui.SecondaryButton
import dev.habita.app.core.ui.TopBar
import dev.habita.app.feature.gym.data.SessionReport
import kotlinx.coroutines.yield

/** Small previews in the filter row: 9:16 at this width in pixels. */
private const val THUMB_WIDTH = 216

private val CardShape = RoundedCornerShape(22.dp)
private val ThumbShape = RoundedCornerShape(12.dp)

/**
 * A photo card for one finished workout: take or pick a photo, choose a filter (Pop to start
 * with), then share it. Opens from the summary or, any time later, from the gym screen.
 */
@Composable
internal fun ShareScreen(report: SessionReport?, onBack: () -> Unit) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }
    var filterName by rememberSaveable { mutableStateOf(ShareFilter.POP.name) }
    val filter = ShareFilter.valueOf(filterName)
    var message by remember { mutableStateOf<String?>(null) }

    val photos = rememberPhotoSource(
        onPhoto = {
            photo = it
            message = null
        },
        onError = { message = "Couldn't open that photo. Try another one." },
    )
    val sharer = rememberImageSharer()

    // Pixel-sized text: the card is drawn at its real size, not the screen's.
    val resolver = LocalFontFamilyResolver.current
    val measurer = remember(resolver) { TextMeasurer(resolver, Density(1f), LayoutDirection.Ltr) }
    val fonts = CardFonts(type.display.fontFamily!!, type.body.fontFamily!!)
    val text = remember(report) { report?.let { ShareCardText.of(it, shortDate(it.startedAt)) } }

    val card = remember(photo, filter, text, measurer) {
        text?.let { renderShareCard(photo, filter, it, measurer, fonts) }
    }
    val thumbs = remember(photo, text, measurer) { mutableStateMapOf<ShareFilter, ImageBitmap>() }
    LaunchedEffect(thumbs) {
        val words = text ?: return@LaunchedEffect
        // One at a time with a pause between, so the screen stays smooth while they fill in.
        ShareFilter.entries.forEach {
            thumbs[it] = renderShareCard(photo, it, words, measurer, fonts, THUMB_WIDTH)
            yield()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Space.screen, vertical = Space.inside)) {
        TopBar("Back", onBack)
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = Space.item), contentAlignment = Alignment.Center) {
            if (card == null) {
                HText("Adding it up…", type.body, color = colors.muted)
            } else {
                Image(
                    card,
                    contentDescription = "Photo card for ${report?.workoutName}, ${filter.label} filter",
                    modifier = Modifier.fillMaxHeight().aspectRatio(9f / 16f, matchHeightConstraintsFirst = true).clip(CardShape),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        if (photos != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.item)) {
                photos.takePhoto?.let { SecondaryButton("Take photo", it, Modifier.weight(1f)) }
                SecondaryButton(if (photo == null) "Choose photo" else "Change photo", photos.choosePhoto, Modifier.weight(1f))
            }
            Gap(Space.item)
        }

        Overline("Filter")
        Gap(Space.inside)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.item), contentPadding = PaddingValues(end = Space.item)) {
            items(ShareFilter.entries, key = { it.name }) { option ->
                FilterThumb(option, thumbs[option], selected = option == filter) {
                    filterName = option.name
                }
            }
        }

        message?.let {
            Gap(Space.inside)
            HText(it, type.caption, color = colors.muted)
        }
        Gap(Space.item)
        if (sharer != null) {
            PrimaryButton(
                "Share",
                onClick = {
                    val image = card ?: return@PrimaryButton
                    val day = report?.startedAt?.let(::backupDate) ?: today()
                    message = if (sharer.share(image, "habita-workout-$day.jpg")) null else "Couldn't share the card. Try again."
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = card != null,
            )
        } else {
            HText("Sharing isn't on this device yet.", type.caption, color = colors.muted)
        }
    }
}

/** One filter in the row: its small preview, ringed when picked, and its name. */
@Composable
private fun FilterThumb(filter: ShareFilter, preview: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    val colors = HabitaTheme.colors
    Column(
        Modifier
            .width(64.dp)
            .clip(ThumbShape)
            .clickable(role = Role.RadioButton, onClickLabel = filter.label, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(ThumbShape)
                .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.border, ThumbShape),
        ) {
            if (preview != null) {
                Image(preview, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Gap(Space.tiny)
        HText(
            filter.label,
            HabitaTheme.typography.caption,
            color = if (selected) colors.accent else colors.muted,
            maxLines = 1,
        )
    }
}