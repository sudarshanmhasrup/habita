package dev.habita.app.feature.food.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
internal actual fun rememberTextCopier(): (String) -> Unit = remember {
    { text -> Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
}