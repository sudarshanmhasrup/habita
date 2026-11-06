package dev.habita.app.compose

import androidx.compose.ui.window.ComposeUIViewController
import dev.habita.app.core.data.IosDatabaseFactory
import dev.habita.app.feature.gym.ios.WaistReminder
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController

/** Created on first use and kept for the life of the process, like HabitaApplication on Android. */
private val app: HabitaApp by lazy { HabitaApp(config = IOS_CONFIG, databases = IosDatabaseFactory()) }

/** The whole app as one view controller, shown by the Swift app in ios/. */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController {
    WaistReminder.start(app.gym)
    lateinit var controller: UIViewController
    controller = ComposeUIViewController {
        // Matches the status bar text to the app's theme, not the system's.
        ComposeApp(app, onDarkThemeChange = { dark ->
            controller.overrideUserInterfaceStyle = if (dark) {
                UIUserInterfaceStyle.UIUserInterfaceStyleDark
            } else {
                UIUserInterfaceStyle.UIUserInterfaceStyleLight
            }
        })
    }
    return controller
}