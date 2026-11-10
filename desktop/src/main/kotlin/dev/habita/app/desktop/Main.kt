package dev.habita.app.desktop

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.habita.app.compose.ComposeApp
import dev.habita.app.compose.HabitaApp
import dev.habita.app.core.data.DesktopDatabaseFactory
import dev.habita.app.core.ui.LocalTitleBarInsets
import kotlin.system.exitProcess

/** Height of the macOS title bar the app draws under; the traffic lights sit inside it. */
private val TitleBarHeight = 32.dp

private val isMac = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

/**
 * The desktop app: the same screens as the phone in one window, wide enough for the two-pane
 * layout. Data lives in ~/.habita and syncs with the server like any other device.
 *
 * On macOS the app fills the whole window, like Android Studio: the title bar is hidden, the
 * traffic lights sit on the app's own background, and the top strip drags the window and
 * zooms it on double-click. Other systems keep their native title bar.
 */
fun main() = application {
    val app = remember { HabitaApp(config = DESKTOP_CONFIG, databases = DesktopDatabaseFactory(), installer = installer()) }
    val icon = remember {
        BitmapPainter(Thread.currentThread().contextClassLoader.getResourceAsStream("habita-icon.png")!!.use(::loadImageBitmap))
    }
    val state = rememberWindowState(width = 1120.dp, height = 780.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "Habita",
        icon = icon,
        state = state,
    ) {
        if (!isMac) {
            ComposeApp(app)
            return@Window
        }
        remember(window) {
            window.rootPane.apply {
                putClientProperty("apple.awt.fullWindowContent", true)
                putClientProperty("apple.awt.transparentTitleBar", true)
                putClientProperty("apple.awt.windowTitleVisible", false)
            }
        }
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalTitleBarInsets provides WindowInsets(top = TitleBarHeight)) {
                ComposeApp(app)
            }
            WindowDraggableArea(
                Modifier
                    .fillMaxWidth()
                    .height(TitleBarHeight)
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { state.toggleZoom() }) },
            ) {}
        }
    }
}

/**
 * The updater, only for an app installed from a published build: the installer's launcher sets
 * `jpackage.app-version`, and CI gives the build its number. Run from source, there's nothing to
 * replace, so no updates are offered.
 */
private fun installer(): DesktopAppInstaller? {
    val system = DesktopSystem.current() ?: return null
    if (System.getProperty("jpackage.app-version") == null || DESKTOP_VERSION_CODE == 0) return null
    return DesktopAppInstaller(system, DESKTOP_VERSION_CODE, quit = { exitProcess(0) })
}

private fun WindowState.toggleZoom() {
    placement = if (placement == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized
}