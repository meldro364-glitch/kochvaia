package com.kochvaia.app.ui.parent

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.kochvaia.app.BuildConfig
import com.kochvaia.app.data.remote.ApiErrorAdapter
import com.kochvaia.app.data.repo.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.TimeZone
import javax.inject.Inject

@HiltViewModel
class ParentSignInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val errors: ApiErrorAdapter,
) : ViewModel() {
    sealed interface UiState {
        data object Idle : UiState
        data object Signing : UiState
        data class Error(val message: String) : UiState
        data object Done : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun signInWith(idToken: String, displayName: String?) {
        _state.value = UiState.Signing
        viewModelScope.launch {
            runCatching {
                authRepository.signInWithGoogle(
                    idToken = idToken,
                    inviteCode = null,
                    familyTz = TimeZone.getDefault().id,
                    displayName = displayName,
                )
            }.onSuccess { _state.value = UiState.Done }
                .onFailure { _state.value = UiState.Error(errors.message(it)) }
        }
    }

    fun fail(message: String) {
        _state.value = UiState.Error(message)
    }
}

@Composable
fun ParentSignInScreen(
    onSignedIn: () -> Unit,
    viewModel: ParentSignInViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is ParentSignInViewModel.UiState.Done) onSignedIn()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Welcome", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Sign in with your Google account to manage your kids and stars.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        when (val s = state) {
            is ParentSignInViewModel.UiState.Idle,
            is ParentSignInViewModel.UiState.Error -> {
                Button(
                    onClick = {
                        launchGoogleSignIn(
                            scope = scope,
                            context = context,
                            onToken = { token, name -> viewModel.signInWith(token, name) },
                            onError = { viewModel.fail(it) },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    enabled = BuildConfig.GOOGLE_OAUTH_CLIENT_ID.isNotEmpty(),
                ) {
                    Text("Sign in with Google", style = MaterialTheme.typography.titleMedium)
                }
                if (BuildConfig.GOOGLE_OAUTH_CLIENT_ID.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "GOOGLE_OAUTH_CLIENT_ID is not configured in this build.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (s is ParentSignInViewModel.UiState.Error) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Sign-in failed: ${s.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            ParentSignInViewModel.UiState.Signing -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Signing in…")
            }
            ParentSignInViewModel.UiState.Done -> Unit
        }
    }
}

/**
 * Calls Credential Manager to obtain a Google ID token. The bottom-sheet UI
 * is hosted by Play Services; we only get the resulting credential.
 */
private fun launchGoogleSignIn(
    scope: CoroutineScope,
    context: Context,
    onToken: (idToken: String, displayName: String?) -> Unit,
    onError: (String) -> Unit,
) {
    val activity = context as? Activity ?: run {
        onError("no_activity_context")
        return
    }
    val clientId = BuildConfig.GOOGLE_OAUTH_CLIENT_ID
    if (clientId.isEmpty()) {
        onError("oauth_client_id_missing")
        return
    }
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(
            GetGoogleIdOption.Builder()
                .setServerClientId(clientId)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build(),
        )
        .build()
    val cm = CredentialManager.create(context)
    scope.launch {
        try {
            val result = cm.getCredential(context = activity, request = request)
            val cred = result.credential
            if (cred is CustomCredential &&
                cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                try {
                    val google = GoogleIdTokenCredential.createFrom(cred.data)
                    onToken(google.idToken, google.displayName)
                } catch (e: GoogleIdTokenParsingException) {
                    onError("invalid_google_id_token")
                }
            } else {
                onError("unexpected_credential_type")
            }
        } catch (e: GetCredentialException) {
            onError(e.type.ifEmpty { e.message ?: "credential_error" })
        } catch (e: Exception) {
            onError(e.message ?: "credential_error")
        }
    }
}
