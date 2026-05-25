package com.kochvaia.app.ui.kid

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import javax.inject.Inject

/**
 * Placeholder kid-pairing screen. v1 accepts the code via text entry; CameraX
 * QR scanning lands in phase 3.
 */
@HiltViewModel
class KidPairingViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val errors: ApiErrorAdapter,
) : ViewModel() {
    sealed interface UiState {
        data object Idle : UiState
        data object Pairing : UiState
        data class Error(val message: String) : UiState
        data object Done : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun submit(code: String) {
        _state.value = UiState.Pairing
        viewModelScope.launch {
            runCatching { auth.redeemKidQr(code, android.os.Build.MODEL) }
                .onSuccess { _state.value = UiState.Done }
                .onFailure { _state.value = UiState.Error(errors.message(it)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidPairingScreen(
    onPaired: () -> Unit,
    onBack: () -> Unit,
    viewModel: KidPairingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var code by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is KidPairingViewModel.UiState.Done) onPaired()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enter pairing code") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Ask a parent to show you the QR code, then type the letters and numbers shown beneath it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(9) },
                label = { Text("Code (e.g. ABCD-EFGH)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.submit(code) },
                enabled = code.length >= 8 && state !is KidPairingViewModel.UiState.Pairing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Connect") }
            Spacer(Modifier.height(16.dp))
            when (val s = state) {
                KidPairingViewModel.UiState.Pairing -> CircularProgressIndicator()
                is KidPairingViewModel.UiState.Error -> Text(
                    "Couldn't connect: ${s.message}",
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Unit
            }
        }
    }
}
