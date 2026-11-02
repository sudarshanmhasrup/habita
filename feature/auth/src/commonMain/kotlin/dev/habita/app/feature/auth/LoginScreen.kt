package dev.habita.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.habita.app.core.Core
import dev.habita.app.core.theme.HabitaTheme
import dev.habita.app.core.ui.Gap
import dev.habita.app.core.ui.HText
import dev.habita.app.core.ui.HabitaScreen
import dev.habita.app.core.ui.LabeledField
import dev.habita.app.core.ui.PrimaryButton

/** Email and password only. Shown whenever there is no login token. */
@Composable
fun LoginScreen(core: Core) {
    val viewModel = viewModel { LoginViewModel(AuthRepository(core)) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onLogin = viewModel::login,
    )
}

@Composable
private fun LoginContent(
    state: LoginState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
) {
    val colors = HabitaTheme.colors
    val type = HabitaTheme.typography
    HabitaScreen {
        // The form sits at the bottom, near the thumb, and scrolls when the keyboard leaves no room.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val minHeight = maxHeight
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minHeight)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                HText("habita", type.display.copy(fontSize = type.display.fontSize * 1.2f))
                Gap(8.dp)
                HText("Your personal space.", type.body, color = colors.muted)
                Gap(40.dp)
                LabeledField(
                    label = "Email",
                    value = state.email,
                    onValueChange = onEmailChange,
                    enabled = !state.loading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                )
                Gap(20.dp)
                LabeledField(
                    label = "Password",
                    value = state.password,
                    onValueChange = onPasswordChange,
                    enabled = !state.loading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onLogin() }),
                )
                Gap(12.dp)
                HText(state.error ?: "", type.caption, color = colors.error)
                Gap(16.dp)
                PrimaryButton(
                    text = if (state.loading) "Logging in…" else "Log in",
                    onClick = onLogin,
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                )
                Gap(16.dp)
                HText(
                    "Only you can sign in",
                    type.caption,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}