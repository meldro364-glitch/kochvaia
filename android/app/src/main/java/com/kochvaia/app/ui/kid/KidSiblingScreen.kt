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
import com.kochvaia.app.data.cache.DashboardLoader
import com.kochvaia.app.data.cache.DaysCache
import com.kochvaia.app.data.cache.KidsCache
import com.kochvaia.app.data.cache.MeCache
import com.kochvaia.app.data.cache.SummariesCache
import com.kochvaia.app.data.remote.ApiErrorAdapter
import com.kochvaia.app.data.remote.DayDto
import com.kochvaia.app.data.remote.KidDto
import com.kochvaia.app.data.remote.SeenStar
import com.kochvaia.app.data.remote.SummaryResponse
import com.kochvaia.app.data.SessionStore
import com.kochvaia.app.data.repo.StarRepository
import com.kochvaia.app.ui.common.StarBurstOverlay
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val stars: StarRepository,
    private val dashboard: DashboardLoader,
    private val meCache: MeCache,
    private val kidsCache: KidsCache,
    private val summariesCache: SummariesCache,
    private val daysCache: DaysCache,
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

    /**
     * Same SWR pattern as KidHomeViewModel — cached state paints first,
     * /dashboard refresh + sibling's week-of-days run in background.
     */
    fun load(kidId: String) {
        val selfKidId = sessionStore.load()?.kidId
        applyFromCaches(kidId, selfKidId)
        viewModelScope.launch {
            val dashResult = dashboard.refresh()
            applyFromCaches(kidId, selfKidId)
            dashResult.onFailure {
                if (_state.value.sibling == null) {
                    _state.value = _state.value.copy(loading = false, error = errors.message(it))
                }
            }
            val anchor = _state.value.weekAnchor
            daysCache.refresh(kidId, anchor.minusDays(6).toString(), anchor.toString())
                .onSuccess { _state.value = _state.value.copy(days = it.days) }
            runCatching { stars.seen(kidId) }
                .onSuccess { s -> _state.value = _state.value.copy(newStars = s.newStars) }
        }
    }

    private fun applyFromCaches(kidId: String, selfKidId: String?) {
        val me = meCache.flow.value
        val kids = kidsCache.flow.value
        val summaries = summariesCache.flow.value
        val tz = me?.family?.tz ?: _state.value.familyTz
        val sib = kids?.firstOrNull { it.id == kidId }
        val anchor = if (tz != "UTC" && me != null) LocalDate.now(ZoneId.of(tz)) else _state.value.weekAnchor
        val cachedDays = daysCache.get(kidId, anchor.minusDays(6).toString(), anchor.toString())?.days
        _state.value = _state.value.copy(
            loading = sib == null && kids == null,
            familyTz = tz,
            sibling = sib,
            selfKidId = selfKidId,
            familyOthers = kids.orEmpty()
                .filter { it.id != kidId }
                .map { FamilyMember(it, summaries[it.id]?.availableStars) },
            weekAnchor = anchor,
            days = cachedDays ?: _state.value.days,
            summary = summaries[kidId] ?: _state.value.summary,
            error = if (sib != null) null else _state.value.error,
        )
    }

    fun shiftWeek(kidId: String, deltaDays: Long) {
        val cur = _state.value
        val today = LocalDate.now(ZoneId.of(cur.familyTz))
        val newAnchor = minOf(cur.weekAnchor.plusDays(deltaDays), today)
        val from = newAnchor.minusDays(6).toString()
        val to = newAnchor.toString()
        val cached = daysCache.get(kidId, from, to)?.days
        _state.value = cur.copy(weekAnchor = newAnchor, days = cached ?: emptyList())
        viewModelScope.launch {
            daysCache.refresh(kidId, from, to)
                .onSuccess { _state.value = _state.value.copy(days = it.days) }
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
                else -> {
                    val today = LocalDate.now(ZoneId.of(state.familyTz))
                    KidProfileLayout(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        header = { isWide ->
                            KidProfileHeader(
                                kid = state.sibling,
                                summary = state.summary,
                                avatarSize = if (isWide) 72.dp else 96.dp,
                                starsStyle = if (isWide) MaterialTheme.typography.displayMedium
                                else MaterialTheme.typography.displayLarge,
                            )
                        },
                        body = {
                            KidProfileWeek(
                                days = state.days,
                                weekAnchor = state.weekAnchor,
                                canGoNext = state.weekAnchor.isBefore(today),
                                onPrevWeek = { viewModel.shiftWeek(kidId, -7) },
                                onNextWeek = { viewModel.shiftWeek(kidId, 7) },
                            )
                            if (state.familyOthers.isNotEmpty()) {
                                Spacer(Modifier.height(24.dp))
                                Text("Family", style = MaterialTheme.typography.titleMedium)
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
                            }
                        },
                    )
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
