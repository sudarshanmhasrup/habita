package dev.habita.app.feature.food.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIPasteboard

@Composable
internal actual fun rememberTextCopier(): (String) -> Unit = remember {
    { text -> UIPasteboard.generalPasteboard.string = text }
}