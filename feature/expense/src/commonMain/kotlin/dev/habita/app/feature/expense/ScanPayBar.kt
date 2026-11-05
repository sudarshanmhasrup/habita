package dev.habita.app.feature.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.ui.ControlShape
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HIcon
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaIcons
import dev.habita.app.feature.expense.ui.rememberUpi

/**
 * The bar at the bottom of home: one big "Scan & pay" button. A scanned UPI QR opens the expense
 * tracker on paying it. Shows nothing where QR payments aren't possible (desktop).
 */
@Composable
fun ScanPayBar(feature: ExpenseFeature, modifier: Modifier = Modifier) {
    val upi = rememberUpi()
    if (!upi.supported) return
    val colors = HabitaTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(ControlShape)
            .background(colors.highlight)
            .clickable(role = Role.Button, onClickLabel = "Scan a UPI QR to pay") {
                upi.scan { scanned -> if (scanned != null) feature.requestPay(scanned) }
            }
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HIcon(HabitaIcons.Scan, contentDescription = null, modifier = Modifier.size(22.dp), tint = colors.onHighlight)
        Gap(10.dp)
        HText("Scan & pay", HabitaTheme.typography.button, color = colors.onHighlight)
    }
}