package com.kochvaia.app.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kochvaia.app.data.remote.ApiErrorAdapter
import com.kochvaia.app.data.repo.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.TimeZone
import javax.inject.Inject

@HiltViewModel
class EmailSignInViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val errors: ApiErrorAdapter,
) : ViewModel() {
    sealed interface UiState {
        data object EnterEmail : UiState
        data object SendingCode : UiState
        data class EnterCode(val email: String) : UiState
        data class Verifying(val email: String) : UiState
        data class Error(val message: String, val stage: Stage) : UiState
        data object Done : UiState
        enum class Stage { Email, Code }
    }

    private val _state = MutableStateFlow<UiState>(UiState.EnterEmail)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun requestCode(email: String) {
        val normalized = email.trim().lowercase()
        if (!normalized.contains('@') || normalized.length > 320) {
            _state.value = UiState.Error("That doesn't look like a valid email.", UiState.Stage.Email)
            return
        }
        _state.value = UiState.SendingCode
        viewModelScope.launch {
            runCatching { auth.requestEmailCode(normalized) }
                .onSuccess { _state.value = UiState.EnterCode(normalized) }
                .onFailure {
                    _state.value = UiState.Error(errors.message(it), UiState.Stage.Email)
                }
        }
    }

    fun resendCode(email: String) {
        viewModelScope.launch {
            runCatching { auth.requestEmailCode(email) }
                // Stay on EnterCode either way; show error inline if it fails.
                .onFailure {
                    _state.value = UiState.Error(errors.message(it), UiState.Stage.Code)
                }
        }
    }

    fun verifyCode(email: String, code: String) {
        val clean = code.filter(Char::isDigit)
        if (clean.length != 6) {
            _state.value = UiState.Error("Enter the 6-digit code from the email.", UiState.Stage.Code)
            return
        }
        _state.value = UiState.Verifying(email)
        viewModelScope.launch {
            runCatching {
                auth.signInWithEmailCode(
                    email = email,
                    code = clean,
                    familyTz = TimeZone.getDefault().id,
                    displayName = null,
                )
            }
                .onSuccess { _state.value = UiState.Done }
                .onFailure {
                    _state.value = UiState.Error(errors.message(it), UiState.Stage.Code)
                }
        }
    }

    fun backToEmail() {
        _state.value = UiState.EnterEmail
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailSignInScreen(
    onSignedIn: () -> Unit,
    onBack: () -> Unit,
    viewModel: EmailSignInViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is EmailSignInViewModel.UiState.Done) onSignedIn()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in with email") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state is EmailSignInViewModel.UiState.EnterCode ||
                            state is EmailSignInViewModel.UiState.Verifying
                        ) {
                            viewModel.backToEmail()
                        } else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            when (val s = state) {
                EmailSignInViewModel.UiState.EnterEmail,
                is EmailSignInViewModel.UiState.Error -> {
                    if (s !is EmailSignInViewModel.UiState.Error || s.stage == EmailSignInViewModel.UiState.Stage.Email) {
                        EmailEntry(
                            email = email,
                            error = (s as? EmailSignInViewModel.UiState.Error)?.message,
                            onChange = { email = it },
                            onSubmit = { viewModel.requestCode(email) },
                        )
                    } else {
                        val errState = s as EmailSignInViewModel.UiState.Error
                        CodeEntry(
                            email = email,
                            code = code,
                            error = errState.message,
                            sending = false,
                            onCodeChange = { code = it },
                            onSubmit = { viewModel.verifyCode(email, code) },
                            onResend = { viewModel.resendCode(email) },
                            onChangeEmail = { viewModel.backToEmail() },
                        )
                    }
                }
                EmailSignInViewModel.UiState.SendingCode -> Spinner("Sending code…")
                is EmailSignInViewModel.UiState.EnterCode -> CodeEntry(
                    email = s.email,
                    code = code,
                    error = null,
                    sending = false,
                    onCodeChange = { code = it },
                    onSubmit = { viewModel.verifyCode(s.email, code) },
                    onResend = { viewModel.resendCode(s.email) },
                    onChangeEmail = { viewModel.backToEmail() },
                )
                is EmailSignInViewModel.UiState.Verifying -> Spinner("Signing in…")
                EmailSignInViewModel.UiState.Done -> Unit
            }
        }
    }
}

@Composable
private fun EmailEntry(
    email: String,
    error: String?,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Text(
        "We'll email you a 6-digit code.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = email,
        onValueChange = onChange,
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onSubmit,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 14.dp),
        enabled = email.isNotBlank(),
    ) { Text("Send code", style = MaterialTheme.typography.titleMedium) }
    if (error != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CodeEntry(
    email: String,
    code: String,
    error: String?,
    sending: Boolean,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    onChangeEmail: () -> Unit,
) {
    Text(
        "We sent a code to",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(email, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = code,
        onValueChange = { onCodeChange(it.filter(Char::isDigit).take(6)) },
        label = { Text("6-digit code") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onSubmit,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 14.dp),
        enabled = code.length == 6 && !sending,
    ) { Text("Sign in", style = MaterialTheme.typography.titleMedium) }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onResend, modifier = Modifier.fillMaxWidth()) {
        Text("Resend code")
    }
    TextButton(onClick = onChangeEmail, modifier = Modifier.fillMaxWidth()) {
        Text("Use a different email")
    }
    if (error != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Spinner(label: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(label)
    }
}
