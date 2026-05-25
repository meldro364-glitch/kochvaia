package com.kochvaia.app.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kochvaia.app.data.remote.ApiErrorAdapter
import com.kochvaia.app.data.repo.AuthRepository
import com.kochvaia.app.ui.common.QrImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ParentShareQrViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val errors: ApiErrorAdapter,
) : ViewModel() {
    data class State(
        val loading: Boolean = false,
        val error: String? = null,
        val code: String? = null,
        val expiresAt: Long? = null,
        val isCoParent: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun generate(kidId: String?) {
        viewModelScope.launch {
            _state.value = State(loading = true, isCoParent = kidId == null)
            runCatching {
                if (kidId == null) auth.createCoParentInviteQr()
                else auth.createKidPairQr(kidId)
            }.onSuccess {
                _state.value = State(
                    loading = false,
                    code = it.code,
                    expiresAt = it.expiresAt,
                    isCoParent = kidId == null,
                )
            }.onFailure {
                _state.value = State(loading = false, error = errors.message(it), isCoParent = kidId == null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentShareQrScreen(
    kidId: String?,
    onBack: () -> Unit,
    viewModel: ParentShareQrViewModel = hiltViewModel(),
) {
    LaunchedEffect(kidId) { viewModel.generate(kidId) }
    val state by viewModel.state.collectAsState()
    val title = if (state.isCoParent) "Invite co-parent" else "Pair kid device"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                state.code != null -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (state.isCoParent)
                            "Scan with the other parent's phone, then they sign in with their Google account."
                        else
                            "Scan from the kid's device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    QrImage(
                        content = "kochvaia://join?code=${state.code}",
                        sizePx = 700,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        state.code!!,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    state.expiresAt?.let { exp ->
                        Text(
                            "Expires at ${formatTime(exp)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(unixMs: Long): String {
    val fmt = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
    return fmt.format(Instant.ofEpochMilli(unixMs))
}
