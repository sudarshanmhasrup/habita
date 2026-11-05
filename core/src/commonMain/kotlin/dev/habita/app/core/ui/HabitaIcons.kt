package dev.habita.app.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons on a 24 × 24 grid, tinted where they are drawn. */
object HabitaIcons {
    val ChevronLeft = icon("ChevronLeft", "M15 6l-6 6 6 6")
    val ChevronRight = icon("ChevronRight", "M9 6l6 6-6 6")
    val Plus = icon("Plus", "M12 5v14M5 12h14")
    val Minus = icon("Minus", "M5 12h14")
    val Dumbbell = icon("Dumbbell", "M3 10v4M6.5 7v10M17.5 7v10M21 10v4M6.5 12h11")
    val Wallet = icon("Wallet", "M4 6h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2zM20 11h-4a2 2 0 0 0 0 4h4")
    val Sun = icon(
        "Sun",
        "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8zM12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2" +
            "M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4",
    )
    val Moon = icon("Moon", "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z")
    val Check = icon("Check", "M5 12.5l4.5 4.5L19 7.5")
    val Clock = icon("Clock", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM12 7v5l3 2")
    val Flame = icon("Flame", "M12 3c1 3.5 5 5.5 5 10a5 5 0 0 1-10 0c0-2.5 1.5-4 2.5-5c0 2 1 3 2 3c0-3-1-5.5 .5-8z")
    val TrendUp = icon("TrendUp", "M4 17l6-6 4 4 6-7M15 8h5v5")
    val Trophy = icon("Trophy", "M8 4h8v5a4 4 0 0 1-8 0zM8 6H5a3 3 0 0 0 3 4M16 6h3a3 3 0 0 1-3 4M12 13v4M8.5 20h7")
    val Play = icon("Play", "M8 5.5v13l10-6.5z")
    val Cloud = icon("Cloud", "M7 18h10a4 4 0 0 0 .5-8A6 6 0 0 0 6 9.5A4.3 4.3 0 0 0 7 18z")
    val Palette = icon(
        "Palette",
        "M12 3a9 9 0 1 0 0 18c.9 0 1.5-.7 1.5-1.5c0-.4-.2-.8-.4-1.1c-.3-.3-.4-.7-.4-1c0-.8.7-1.5 1.5-1.5H16a5 5 0 0 0 5-5c0-4.4-4-7.9-9-7.9zM7.5 11a1 1 0 1 0 0 2a1 1 0 1 0 0-2zM10 6.8a1 1 0 1 0 0 2a1 1 0 1 0 0-2zM15 6.8a1 1 0 1 0 0 2a1 1 0 1 0 0-2z",
    )
    val Phone = icon("Phone", "M8 3h8a1.5 1.5 0 0 1 1.5 1.5v15A1.5 1.5 0 0 1 16 21H8a1.5 1.5 0 0 1-1.5-1.5v-15A1.5 1.5 0 0 1 8 3zM11 18h2")

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
}