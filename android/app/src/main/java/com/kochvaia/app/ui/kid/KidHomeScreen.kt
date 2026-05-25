package com.kochvaia.app.ui.kid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kochvaia.app.data.SessionStore
import com.kochvaia.app.data.remote.ApiErrorAdapter
import com.kochvaia.app.data.remote.DayDto
import com.kochvaia.app.data.remote.SummaryResponse
import com.kochvaia.app.data.repo.StarRepository
import com.kochvaia.app.ui.common.DayStrip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * v1 kid home screen — shows the kid's own 7-day strip + summary. Phase 3
 * adds week-back paging, sibling list, and the /seen-driven animations.
 */
@HiltViewModel
class KidHomeViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val stars: StarRepository,
    private val errors: ApiErrorAdapter,
) : ViewModel() {
    data class State(
        val loading: Boolean = true,
        val error: String? = null,
        val days: List<DayDto> = emptyList(),
        val summary: SummaryResponse? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun load() {
        val kidId = sessionStore.load()?.kidId ?: run {
            _state.value = State(loading = false, error = "no_kid_session")
            return
        }
        viewModelScope.launch {
            _state.value = State(loading = true)
            runCatching {
                val today = LocalDate.now()
                val from = today.minusDays(6)
                val days = stars.days(kidId, from.toString(), today.toString()).days
                val summary = stars.summary(kidId)
                // Fire the seen call to advance the checkpoint (animations TBD).
                runCatching { stars.seen(kidId) }
                State(loading = false, days = days, summary = summary)
            }.onSuccess { _state.value = it }
                .onFailure { _state.value = State(loading = false, error = errors.message(it)) }
        }
    }
}

@Composable
fun KidHomeScreen(
    onOpenSibling: (String) -> Unit,
    viewModel: KidHomeViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsState()
    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                else -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                ) {
                    Text(
                        "${state.summary?.availableStars ?: 0} ⭐",
                        style = MaterialTheme.typography.displayLarge,
                    )
                    Text(
                        "available right now",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    Text("Last 7 days", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    DayStrip(days = state.days)
                }
            }
        }
    }
}
