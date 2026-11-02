package dev.habita.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.habita.app.core.network.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotEmpty() && !loading
}

class LoginViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onEmailChange(email: String) = _state.update { it.copy(email = email, error = null) }

    fun onPasswordChange(password: String) = _state.update { it.copy(password = password, error = null) }

    /** On success the saved token switches the app to the home screen; nothing else to do here. */
    fun login() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val error = try {
                repository.login(current.email, current.password)
                null
            } catch (e: ApiException) {
                e.toMessage()
            }
            _state.update { it.copy(loading = false, error = error, password = if (error == null) "" else it.password) }
        }
    }

    private fun ApiException.toMessage(): String = when (this) {
        is ApiException.NotConfigured -> "No server address. Set HABITA_SERVER_URL in local.properties and rebuild."
        is ApiException.Offline -> "Can't reach the server. Check your connection."
        is ApiException.Unauthorized -> "Wrong email or password."
        is ApiException.Server -> if (status == 429) "Too many attempts. Wait a minute and try again." else "Server error: $message"
    }
}