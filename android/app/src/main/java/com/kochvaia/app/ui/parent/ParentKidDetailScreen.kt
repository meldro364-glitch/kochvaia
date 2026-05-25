package com.kochvaia.app.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kochvaia.app.data.remote.ApiErrorAdapter
import com.kochvaia.app.data.remote.DayDto
import com.kochvaia.app.data.remote.SummaryResponse
import com.kochvaia.app.data.repo.KidRepository
import com.kochvaia.app.data.repo.StarRepository
import com.kochvaia.app.ui.common.DayStrip
import com.kochvaia.app.ui.common.WeekHeader
import com.kochvaia.app.ui.common.weekRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class ParentKidDetailViewModel @Inject constructor(
    private val stars: StarRepository,
    private val kidsRepo: KidRepository,
    private val errors: ApiErrorAdapter,
    savedState: SavedStateHandle,
) : ViewModel() {
    // kidId is provided by the route — but we don't read it here; the screen
    // passes it in load(). This keeps the VM testable without the SavedStateHandle.
    @Suppress("unused") private val _ssh = savedState

    data class State(
        val loading: Boolean = true,
        val error: String? = null,
        val kidName: String? = null,
        val avatarEmoji: String = "⭐",
        val familyTz: String? = null,
        val weekAnchor: LocalDate = LocalDate.now(),
        val days: List<DayDto> = emptyList(),
        val summary: SummaryResponse? = null,
        val toast: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun load(kidId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val me = kidsRepo.me()
                val list = kidsRepo.list()
                val kid = list.firstOrNull { it.id == kidId }
                    ?: error("kid_not_in_family")
                val tz = me.family.tz
                val anchor = LocalDate.now(ZoneId.of(tz))
                val (from, to) = weekRange(anchor)
                val days = stars.days(kidId, from.toString(), to.toString()).days
                val summary = stars.summary(kidId)
                _state.value = State(
                    loading = false,
                    kidName = kid.displayName,
                    avatarEmoji = kid.avatarEmoji,
                    familyTz = tz,
                    weekAnchor = anchor,
                    days = days,
                    summary = summary,
                )
            }.onFailure { _state.value = _state.value.copy(loading = false, error = errors.message(it)) }
        }
    }

    fun shiftWeek(kidId: String, deltaDays: Long) {
        val cur = _state.value
        if (cur.familyTz == null) return
        val newAnchor = cur.weekAnchor.plusDays(deltaDays)
        // Don't allow scrolling into the future past "this week".
        val today = LocalDate.now(ZoneId.of(cur.familyTz))
        val capped = minOf(newAnchor, today)
        loadWeek(kidId, capped)
    }

    private fun loadWeek(kidId: String, anchor: LocalDate) {
        viewModelScope.launch {
            val (from, to) = weekRange(anchor)
            runCatching { stars.days(kidId, from.toString(), to.toString()).days }
                .onSuccess { _state.value = _state.value.copy(weekAnchor = anchor, days = it) }
                .onFailure { _state.value = _state.value.copy(toast = errors.message(it)) }
        }
    }

    fun awardOn(kidId: String, dateIso: String) {
        viewModelScope.launch {
            runCatching { stars.award(kidId, dateIso) }
                .onSuccess { reload(kidId) }
                .onFailure { _state.value = _state.value.copy(toast = errors.message(it)) }
        }
    }

    fun deduct(kidId: String, count: Int, reason: String?) {
        viewModelScope.launch {
            runCatching { stars.deduct(kidId, count, reason) }
                .onSuccess { reload(kidId) }
                .onFailure { _state.value = _state.value.copy(toast = errors.message(it)) }
        }
    }

    fun undo(kidId: String, dateIso: String) {
        viewModelScope.launch {
            runCatching { stars.undo(kidId, dateIso) }
                .onSuccess { reload(kidId) }
                .onFailure { _state.value = _state.value.copy(toast = errors.message(it)) }
        }
    }

    private fun reload(kidId: String) {
        val cur = _state.value
        viewModelScope.launch {
            val (from, to) = weekRange(cur.weekAnchor)
            val days = runCatching { stars.days(kidId, from.toString(), to.toString()).days }.getOrNull()
            val summary = runCatching { stars.summary(kidId) }.getOrNull()
            _state.value = cur.copy(
                days = days ?: cur.days,
                summary = summary ?: cur.summary,
            )
        }
    }

    fun consumeToast() { _state.value = _state.value.copy(toast = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentKidDetailScreen(
    kidId: String,
    onBack: () -> Unit,
    viewModel: ParentKidDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(kidId) { viewModel.load(kidId) }
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showDeduct by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.avatarEmoji, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(0.dp))
                        Text(
                            "  ${state.kidName ?: ""}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    "Couldn't load: ${state.error}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                else -> Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    SummaryCard(summary = state.summary)
                    val today = state.familyTz?.let { LocalDate.now(ZoneId.of(it)) }
                    WeekHeader(
                        anchor = state.weekAnchor,
                        canGoNext = today != null && state.weekAnchor.isBefore(today),
                        onPrev = { viewModel.shiftWeek(kidId, -7) },
                        onNext = { viewModel.shiftWeek(kidId, 7) },
                    )
                    DayStrip(
                        days = state.days,
                        onTap = { dateIso ->
                            // Tap a day to award if "none", or to ask about undo if "given".
                            val st = state.days.firstOrNull { it.date == dateIso }?.status
                            when (st) {
                                "none" -> pendingDate = dateIso
                                "given" -> pendingDate = dateIso // confirm undo
                                else -> Unit
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { showDeduct = true },
                            modifier = Modifier.weight(1f),
                            enabled = (state.summary?.availableStars ?: 0) > 0,
                        ) { Text("Deduct stars") }
                    }
                }
            }
        }
    }

    pendingDate?.let { dateIso ->
        val status = state.days.firstOrNull { it.date == dateIso }?.status
        AlertDialog(
            onDismissRequest = { pendingDate = null },
            title = {
                Text(
                    when (status) {
                        "none" -> "Give a star?"
                        "given" -> "Undo this star?"
                        else -> "Action"
                    },
                )
            },
            text = { Text("For $dateIso") },
            confirmButton = {
                TextButton(onClick = {
                    when (status) {
                        "none" -> viewModel.awardOn(kidId, dateIso)
                        "given" -> viewModel.undo(kidId, dateIso)
                        else -> Unit
                    }
                    pendingDate = null
                }) { Text(if (status == "given") "Undo" else "Give") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDate = null }) { Text("Cancel") }
            },
        )
    }

    if (showDeduct) {
        DeductDialog(
            available = state.summary?.availableStars ?: 0,
            onDismiss = { showDeduct = false },
            onConfirm = { n, reason ->
                viewModel.deduct(kidId, n, reason)
                showDeduct = false
            },
        )
    }
}

@Composable
private fun SummaryCard(summary: SummaryResponse?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatColumn("Available", summary?.availableStars ?: 0)
            StatColumn("Earned", summary?.totalEarned ?: 0)
            StatColumn("Used", summary?.totalUsed ?: 0)
        }
    }
}

@Composable
private fun StatColumn(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DeductDialog(
    available: Int,
    onDismiss: () -> Unit,
    onConfirm: (count: Int, reason: String?) -> Unit,
) {
    var countText by remember { mutableStateOf("1") }
    var reason by remember { mutableStateOf("") }
    val parsed = countText.toIntOrNull()
    val ok = parsed != null && parsed in 1..available

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Deduct stars") },
        text = {
            Column {
                Text(
                    "$available available. Deducting consumes the oldest stars first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = countText,
                    onValueChange = { countText = it.filter(Char::isDigit).take(4) },
                    label = { Text("How many?") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(120) },
                    label = { Text("Why? (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = ok,
                onClick = { onConfirm(parsed!!, reason.trim().ifBlank { null }) },
            ) { Text("Deduct") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
