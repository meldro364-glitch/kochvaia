package com.kochvaia.app.ui.kid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kochvaia.app.data.remote.ApiErrorAdapter
import com.kochvaia.app.data.remote.DayDto
import com.kochvaia.app.data.remote.KidDto
import com.kochvaia.app.data.remote.SeenStar
import com.kochvaia.app.data.remote.SummaryResponse
import com.kochvaia.app.data.repo.KidRepository
import com.kochvaia.app.data.SessionStore
import com.kochvaia.app.data.repo.StarRepository
import com.kochvaia.app.ui.common.StarBurstOverlay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class KidSiblingViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val kidsRepo: KidRepository,
    private val stars: StarRepository,
    private val errors: ApiErrorAdapter,
) : ViewModel() {
    data class State(
        val loading: Boolean = true,
        val error: String? = null,
        val familyTz: String = "UTC",
        val sibling: KidDto? = null,
        val selfKidId: String? = null,
        val familyOthers: List<FamilyMember> = emptyList(),
        val weekAnchor: LocalDate = LocalDate.now(),
        val days: List<DayDto> = emptyList(),
        val summary: SummaryResponse? = null,
        val newStars: List<SeenStar> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun load(kidId: String) {
        val selfKidId = sessionStore.load()?.kidId
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                coroutineScope {
                    val meDeferred = async { kidsRepo.me() }
                    val listDeferred = async { kidsRepo.list() }
                    val me = meDeferred.await()
                    val list = listDeferred.await()
                    val sib = list.firstOrNull { it.id == kidId }
                    if (sib == null) {
                        _state.value = State(loading = false, error = "Sibling not found")
                        return@coroutineScope null
                    }
                    val tz = me.family.tz
                    val anchor = LocalDate.now(ZoneId.of(tz))
                    val from = anchor.minusDays(6).toString()
                    val to = anchor.toString()
                    val daysDef = async { stars.days(kidId, from, to).days }
                    val summaryDef = async { stars.summary(kidId) }
                    // /seen is best-effort: failure means we just skip animations.
                    val seenDef = async { runCatching { stars.seen(kidId) }.getOrNull() }
                    // Family row shows everyone except the currently-viewed kid
                    // (which is rendered as the big avatar at the top).
                    val familyOthersDef = list.filter { it.id != kidId }.map { other ->
                        async {
                            FamilyMember(
                                other,
                                runCatching { stars.summary(other.id).availableStars }.getOrNull(),
                            )
                        }
                    }
                    State(
                        loading = false,
                        familyTz = tz,
                        sibling = sib,
                        selfKidId = selfKidId,
                        familyOthers = familyOthersDef.awaitAll(),
                        weekAnchor = anchor,
                        days = daysDef.await(),
                        summary = summaryDef.await(),
                        newStars = seenDef.await()?.newStars.orEmpty(),
                    )
                }
            }.onSuccess { if (it != null) _state.value = it }
                .onFailure { _state.value = State(loading = false, error = errors.message(it)) }
        }
    }

    fun shiftWeek(kidId: String, deltaDays: Long) {
        val cur = _state.value
        val today = LocalDate.now(ZoneId.of(cur.familyTz))
        val newAnchor = minOf(cur.weekAnchor.plusDays(deltaDays), today)
        viewModelScope.launch {
            runCatching {
                stars.days(kidId, newAnchor.minusDays(6).toString(), newAnchor.toString()).days
            }.onSuccess { _state.value = cur.copy(weekAnchor = newAnchor, days = it) }
        }
    }

    fun consumeAnimations() {
        _state.value = _state.value.copy(newStars = emptyList())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidSiblingScreen(
    kidId: String,
    onBack: () -> Unit,
    onOpenOwnHome: () -> Unit,
    onOpenSibling: (String) -> Unit,
    viewModel: KidSiblingViewModel = hiltViewModel(),
) {
    LaunchedEffect(kidId) { viewModel.load(kidId) }
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.sibling?.displayName ?: "Sibling") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val err = state.error
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                err != null -> Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                ) {
                    val today = LocalDate.now(ZoneId.of(state.familyTz))
                    KidProfileBlock(
                        kid = state.sibling,
                        summary = state.summary,
                        days = state.days,
                        weekAnchor = state.weekAnchor,
                        canGoNext = state.weekAnchor.isBefore(today),
                        onPrevWeek = { viewModel.shiftWeek(kidId, -7) },
                        onNextWeek = { viewModel.shiftWeek(kidId, 7) },
                    )
                    if (state.familyOthers.isNotEmpty()) {
                        Spacer(Modifier.height(32.dp))
                        Text(
                            "Family",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(state.familyOthers, key = { it.kid.id }) { row ->
                                SiblingChip(
                                    kid = row.kid,
                                    availableStars = row.availableStars,
                                    onClick = {
                                        if (row.kid.id == state.selfKidId) onOpenOwnHome()
                                        else onOpenSibling(row.kid.id)
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(48.dp))
                    }
                }
            }
            if (state.newStars.isNotEmpty()) {
                StarBurstOverlay(
                    stars = state.newStars,
                    onFinished = { viewModel.consumeAnimations() },
                )
            }
        }
    }
}
