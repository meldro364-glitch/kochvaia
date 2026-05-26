package com.kochvaia.app.ui.kid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kochvaia.app.data.SessionStore
import com.kochvaia.app.data.cache.DashboardLoader
import com.kochvaia.app.data.cache.DaysCache
import com.kochvaia.app.data.cache.ItemsCache
import com.kochvaia.app.data.cache.KidsCache
import com.kochvaia.app.data.cache.MeCache
import com.kochvaia.app.data.cache.SummariesCache
import com.kochvaia.app.data.remote.ApiErrorAdapter
import com.kochvaia.app.data.remote.DayDto
import com.kochvaia.app.data.remote.ItemDto
import com.kochvaia.app.data.remote.KidDto
import com.kochvaia.app.data.remote.SeenStar
import com.kochvaia.app.data.remote.SummaryResponse
import com.kochvaia.app.data.repo.AuthRepository
import com.kochvaia.app.data.repo.StarRepository
import com.kochvaia.app.ui.common.StarBurstOverlay
import com.kochvaia.app.ui.common.detectLongHold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class KidHomeViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val stars: StarRepository,
    private val auth: AuthRepository,
    private val dashboard: DashboardLoader,
    private val meCache: MeCache,
    private val kidsCache: KidsCache,
    private val itemsCache: ItemsCache,
    private val summariesCache: SummariesCache,
    private val daysCache: DaysCache,
    private val errors: ApiErrorAdapter,
) : ViewModel() {
    data class State(
        val loading: Boolean = true,
        val error: String? = null,
        val familyTz: String = "UTC",
        val self: KidDto? = null,
        val siblings: List<FamilyMember> = emptyList(),
        val weekAnchor: LocalDate = LocalDate.now(),
        val days: List<DayDto> = emptyList(),
        val summary: SummaryResponse? = null,
        val newStars: List<SeenStar> = emptyList(),
        val rewards: List<ItemDto> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Renders the cached snapshot synchronously (no UI flicker), then runs a
     * single /dashboard fetch in the background to refresh every per-kid
     * cache at once. The week-of-days and /seen animations are still per-kid
     * and run alongside as they don't fit the /dashboard payload.
     */
    fun load() {
        val kidId = sessionStore.load()?.kidId ?: run {
            _state.value = State(loading = false, error = "Not signed in")
            return
        }
        // 1) Hydrate state from caches synchronously — first paint = real data.
        applyFromCaches(kidId)
        // 2) Refresh in background.
        viewModelScope.launch {
            val dashResult = dashboard.refresh()
            // Re-apply state from caches after dashboard refresh.
            applyFromCaches(kidId)
            dashResult.onFailure {
                if (_state.value.self == null) {
                    _state.value = _state.value.copy(loading = false, error = errors.message(it))
                }
            }
            val tz = _state.value.familyTz
            val anchor = if (tz != "UTC") LocalDate.now(ZoneId.of(tz)) else _state.value.weekAnchor
            daysCache.refresh(kidId, anchor.minusDays(6).toString(), anchor.toString())
                .onSuccess { _state.value = _state.value.copy(weekAnchor = anchor, days = it.days) }
            // /seen is best-effort and not cached — every call returns the
            // delta since the last call, so cache hits would lose data.
            runCatching { stars.seen(kidId) }
                .onSuccess { s -> _state.value = _state.value.copy(newStars = s.newStars) }
        }
    }

    /** Project the current cache state into the screen's local State data class. */
    private fun applyFromCaches(kidId: String) {
        val me = meCache.flow.value
        val kids = kidsCache.flow.value
        val summaries = summariesCache.flow.value
        val rewards = itemsCache.flow.value
        val tz = me?.family?.tz ?: _state.value.familyTz
        val self = kids?.firstOrNull { it.id == kidId }
        val anchor = if (tz != "UTC" && me != null) LocalDate.now(ZoneId.of(tz)) else _state.value.weekAnchor
        val cachedDays = daysCache.get(kidId, anchor.minusDays(6).toString(), anchor.toString())?.days
        _state.value = _state.value.copy(
            loading = self == null && kids == null,
            familyTz = tz,
            self = self,
            siblings = kids.orEmpty()
                .filter { it.id != kidId }
                .map { FamilyMember(it, summaries[it.id]?.availableStars) },
            weekAnchor = anchor,
            days = cachedDays ?: _state.value.days,
            summary = summaries[kidId] ?: _state.value.summary,
            rewards = rewards.orEmpty(),
            error = if (self != null) null else _state.value.error,
        )
    }

    fun shiftWeek(deltaDays: Long) {
        val kidId = sessionStore.load()?.kidId ?: return
        val cur = _state.value
        val today = LocalDate.now(ZoneId.of(cur.familyTz))
        val newAnchor = minOf(cur.weekAnchor.plusDays(deltaDays), today)
        val from = newAnchor.minusDays(6).toString()
        val to = newAnchor.toString()
        // Show cached days if we have them so the chevron tap feels instant.
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

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            auth.logout()
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidHomeScreen(
    onOpenSibling: (String) -> Unit,
    onSignedOut: () -> Unit,
    viewModel: KidHomeViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsState()
    var showSignOutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            // Replaces the empty cream band at the top with a friendly
            // greeting + the current weekday/date. Title uses centered
            // alignment so it looks at home in a kid-friendly screen.
            val today = LocalDate.now(
                runCatching { ZoneId.of(state.familyTz) }.getOrDefault(ZoneId.systemDefault()),
            )
            val dateFmt = remember {
                DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
            }
            val greeting = state.self?.displayName?.let { "Hi, $it 👋" } ?: "Hi 👋"
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            greeting,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        )
                        Text(
                            today.format(dateFmt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
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
                                kid = state.self,
                                summary = state.summary,
                                avatarModifier = Modifier.pointerInput(Unit) {
                                    detectLongHold(holdMs = 1500L) { showSignOutDialog = true }
                                },
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
                                onPrevWeek = { viewModel.shiftWeek(-7) },
                                onNextWeek = { viewModel.shiftWeek(7) },
                            )
                            if (state.siblings.isNotEmpty()) {
                                Spacer(Modifier.height(24.dp))
                                Text("Family", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    items(state.siblings, key = { it.kid.id }) { row ->
                                        SiblingChip(
                                            kid = row.kid,
                                            availableStars = row.availableStars,
                                            onClick = { onOpenSibling(row.kid.id) },
                                        )
                                    }
                                }
                            }
                            if (state.rewards.isNotEmpty()) {
                                Spacer(Modifier.height(24.dp))
                                Text("Rewards", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                RewardsList(
                                    rewards = state.rewards,
                                    availableStars = state.summary?.availableStars ?: 0,
                                )
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

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out of this device?") },
            text = { Text("You'll need a new QR code from a parent to sign back in.") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    viewModel.signOut(onSignedOut)
                }) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") } },
        )
    }
}
