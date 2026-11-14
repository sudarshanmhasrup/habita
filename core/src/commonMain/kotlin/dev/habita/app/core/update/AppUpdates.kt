package dev.habita.app.core.update

import dev.habita.app.core.Core
import dev.habita.app.core.network.ApiException
import dev.habita.app.shared.api.AppRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.hours

/**
 * Installs a downloaded build on this platform: Android, and the installed desktop apps. iOS apps
 * update through Xcode or the App Store.
 */
interface AppInstaller {
    /** The server's name for this platform's builds (`android`, `windows`, `mac` or `linux`). */
    val platform: String

    /** What the update card says while the system installer takes over. */
    val installingHint: String

    /** The build running now; a release with a higher number is an update. */
    val versionCode: Int

    /**
     * Downloads [release] with [download], checks it, and hands it to the system installer, which
     * asks the user to confirm. Throws [UpdateException] with a message for the user when it
     * can't.
     */
    suspend fun install(release: AppRelease, download: suspend (onProgress: (Float) -> Unit, write: (ByteArray, Int) -> Unit) -> Unit, onProgress: (Float) -> Unit)
}

class UpdateException(message: String) : Exception(message)

sealed interface UpdateState {
    data object None : UpdateState

    data class Available(val release: AppRelease) : UpdateState

    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState

    /** Handed to the system installer; [hint] says what happens next. */
    data class Installing(val release: AppRelease, val hint: String) : UpdateState

    data class Failed(val release: AppRelease, val message: String) : UpdateState
}

/**
 * Checks the server for a newer build when the app starts, after login and every few hours while
 * it runs, and installs it when the user taps Install. Nothing installs without the user.
 */
class AppUpdates(private val core: Core, private val installer: AppInstaller) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<UpdateState>(UpdateState.None)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun start() {
        core.scope.launch {
            core.settings.token.collect { if (it != null) check() }
        }
        core.scope.launch {
            while (true) {
                delay(CHECK_EVERY)
                check()
            }
        }
    }

    /** Asks the server for the newest build. Quietly does nothing when offline. */
    suspend fun check() {
        if (core.settings.token.value == null) return
        val latest = try {
            core.api.getOrNull<AppRelease>("app/${installer.platform}/latest")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        mutex.withLock {
            val busy = _state.value is UpdateState.Downloading || _state.value is UpdateState.Installing
            if (!busy) {
                _state.value = if (latest != null && latest.versionCode > installer.versionCode) UpdateState.Available(latest) else UpdateState.None
            }
        }
    }

    fun install() {
        val offered = when (val current = _state.value) {
            is UpdateState.Available -> current.release
            is UpdateState.Failed -> current.release
            else -> return
        }
        core.scope.launch {
            _state.value = UpdateState.Downloading(offered, 0f)
            // The server only serves its newest build, which may be newer than the one offered
            // hours ago. Checking against the offered build's SHA-256 would then call a good
            // download damaged, so ask again and install what will actually be downloaded.
            val release = try {
                core.api.getOrNull<AppRelease>("app/${installer.platform}/latest")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = UpdateState.Failed(offered, "Couldn't download the update. Check your connection.")
                return@launch
            }
            if (release == null || release.versionCode <= installer.versionCode) {
                _state.value = UpdateState.None
                return@launch
            }
            _state.value = UpdateState.Downloading(release, 0f)
            try {
                installer.install(
                    release = release,
                    download = { onProgress, write -> core.api.download("app/${installer.platform}/download", onProgress, write) },
                    onProgress = { _state.value = UpdateState.Downloading(release, it) },
                )
                _state.value = UpdateState.Installing(release, installer.installingHint)
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateException) {
                _state.value = UpdateState.Failed(release, e.message ?: "Couldn't install the update.")
            } catch (_: ApiException) {
                _state.value = UpdateState.Failed(release, "Couldn't download the update. Check your connection.")
            } catch (_: Exception) {
                _state.value = UpdateState.Failed(release, "Couldn't install the update.")
            }
        }
    }

    private companion object {
        val CHECK_EVERY = 6.hours
    }
}