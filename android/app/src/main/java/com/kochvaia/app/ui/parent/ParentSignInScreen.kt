package com.kochvaia.app.ui.parent

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
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

    /**
     * Co-parent join: set when the user scanned/typed an invite code coming
     * from a parent QR. Carried through to both the Google and email sign-in
     * paths so the backend attaches them to the existing family instead of
     * creating a new one. Null = normal sign-in (fresh family).
     */
    private val _inviteCode = MutableStateFlow<String?>(null)
    val inviteCode: StateFlow<String?> = _inviteCode.asStateFlow()

    fun setInviteCode(code: String?) {
        _inviteCode.value = code?.trim()?.uppercase()?.ifBlank { null }
    }

    fun signInWith(idToken: String, displayName: String?) {
        _state.value = UiState.Signing
        viewModelScope.launch {
            runCatching {
                authRepository.signInWithGoogle(
                    idToken = idToken,
                    inviteCode = _inviteCode.value,
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

    /** Quiet reset for user-initiated cancellations (no error chrome). */
    fun cancel() {
        _state.value = UiState.Idle
    }
}

@Composable
fun ParentSignInScreen(
    onSignedIn: () -> Unit,
    onUseEmail: (inviteCode: String?) -> Unit,
    initialInviteCode: String? = null,
    viewModel: ParentSignInViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()
    val inviteCode by viewModel.inviteCode.collectAsState()
    var showInviteDialog by remember { mutableStateOf(false) }
    // Detect Google Play Services once per composition. On devices without GMS
    // (e.g. Amazon Fire tablets) Credential Manager throws
    // TYPE_GET_CREDENTIAL_PROVIDER_CONFIGURATION_EXCEPTION, so we hide the
    // Google path entirely and route to email sign-in.
    val hasGms = remember { isGooglePlayServicesAvailable(context) }

    // Hydrate any code arriving via deep link or nav arg.
    LaunchedEffect(initialInviteCode) {
        if (!initialInviteCode.isNullOrBlank()) viewModel.setInviteCode(initialInviteCode)
    }

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
            if (hasGms) {
                "Sign in to manage your kids and stars."
            } else {
                "Sign in with your email to manage your kids and stars."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        when (val s = state) {
            is ParentSignInViewModel.UiState.Idle,
            is ParentSignInViewModel.UiState.Error -> {
                if (hasGms) {
                    Button(
                        onClick = {
                            launchGoogleSignIn(
                                scope = scope,
                                context = context,
                                onToken = { token, name -> viewModel.signInWith(token, name) },
                                onError = { viewModel.fail(it) },
                                onCanceled = { viewModel.cancel() },
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
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { onUseEmail(inviteCode) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Text("Sign in with email", style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    Button(
                        onClick = { onUseEmail(inviteCode) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Text("Sign in with email", style = MaterialTheme.typography.titleMedium)
                    }
                }

                // Co-parent join: optional invite code. Hidden behind a single
                // text link by default; once set, displays the code so the
                // user can see / clear / change it before signing in.
                Spacer(Modifier.height(16.dp))
                if (inviteCode == null) {
                    TextButton(onClick = { showInviteDialog = true }) {
                        Text("Joining an existing family? Enter invite code")
                    }
                } else {
                    Text(
                        "Invite code: $inviteCode",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "You'll join the existing family after sign-in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { viewModel.setInviteCode(null) }) {
                        Text("Clear code")
                    }
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

    if (showInviteDialog) {
        InviteCodeDialog(
            initial = inviteCode.orEmpty(),
            onDismiss = { showInviteDialog = false },
            onSave = { code ->
                viewModel.setInviteCode(code.ifBlank { null })
                showInviteDialog = false
            },
        )
    }
}

@Composable
private fun InviteCodeDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite code") },
        text = {
            Column {
                Text(
                    "Type the code shown beneath the QR on the other parent's phone (e.g. ABCD-EFGH).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { raw ->
                        text = raw.uppercase()
                            .filter { it.isLetterOrDigit() || it == '-' }
                            .take(9)
                    },
                    singleLine = true,
                    label = { Text("ABCD-EFGH") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text.trim()) },
                enabled = text.trim().length >= 8,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Cheap GMS-availability probe. We deliberately avoid pulling in the
 * play-services-base GoogleApiAvailability dependency just for this check —
 * the package query is enough to decide whether Credential Manager can
 * actually find a Google provider.
 */
private fun isGooglePlayServicesAvailable(context: Context): Boolean = try {
    context.packageManager.getPackageInfo("com.google.android.gms", 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

/**
 * Calls Credential Manager to obtain a Google ID token. Uses
 * [GetSignInWithGoogleOption] — the explicit-button variant — because the
 * user tapped a labeled "Sign in with Google" button and expects the account
 * picker every time. Avoids the silent TYPE_NO_CREDENTIAL outcomes that
 * GetGoogleIdOption can produce after a recent dismissal or when the
 * device's account list doesn't match its filter.
 */
private fun launchGoogleSignIn(
    scope: CoroutineScope,
    context: Context,
    onToken: (idToken: String, displayName: String?) -> Unit,
    onError: (String) -> Unit,
    onCanceled: () -> Unit,
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
        .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
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
                    onError("Couldn't read your Google account. Try email sign-in instead.")
                }
            } else {
                onError("Couldn't read your Google account. Try email sign-in instead.")
            }
        } catch (e: GetCredentialCancellationException) {
            // User tapped outside or pressed Back — not an error, just reset.
            onCanceled()
        } catch (e: NoCredentialException) {
            onError("No Google account available. Try email sign-in instead.")
        } catch (e: GetCredentialProviderConfigurationException) {
            onError("Google sign-in isn't available on this device. Use email instead.")
        } catch (e: GetCredentialException) {
            onError("Google sign-in failed. Try email sign-in instead.")
        } catch (e: Exception) {
            onError(e.message ?: "Sign-in failed. Try email sign-in instead.")
        }
    }
}
