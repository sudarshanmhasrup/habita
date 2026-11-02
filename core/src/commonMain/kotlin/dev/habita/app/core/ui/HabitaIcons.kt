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