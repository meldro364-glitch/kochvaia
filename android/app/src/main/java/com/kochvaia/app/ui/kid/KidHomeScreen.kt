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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.kochvaia.app.data.remote.ApiErrorAdapter
import com.kochvaia.app.data.remote.DayDto
import com.kochvaia.app.data.remote.ItemDto
import com.kochvaia.app.data.remote.KidDto
import com.kochvaia.app.data.remote.SeenStar
import com.kochvaia.app.data.remote.SummaryResponse
import com.kochvaia.app.data.repo.AuthRepository
import com.kochvaia.app.data.repo.ItemRepository
import com.kochvaia.app.data.repo.KidRepository
import com.kochvaia.app.data.repo.StarRepository
import com.kochvaia.app.ui.common.StarBurstOverlay
import com.kochvaia.app.ui.common.detectLongHold
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
class KidHomeViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val kidsRepo: KidRepository,
    private val stars: StarRepository,
    private val items: ItemRepository,
    private val auth: AuthRepository,
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

    fun load() {
        val kidId = sessionStore.load()?.kidId ?: run {
            _state.value = State(loading = false, error = "Not signed in")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                // Resolve family + kid list once, then fan out all per-kid calls
                // in parallel — they're independent and each is a round-trip.
                coroutineScope {
                    val meDeferred = async { kidsRepo.me() }
                    val listDeferred = async { kidsRepo.list() }
                    val me = meDeferred.await()
                    val list = listDeferred.await()
                    val self = list.firstOrNull { it.id == kidId }
                    if (self == null) {
                        _state.value = State(loading = false, error = "Your profile isn't in this family.")
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
                    // Items are best-effort too — an item failure shouldn't
                    // hide the rest of the home screen.
                    val itemsDef = async { runCatching { items.list() }.getOrDefault(emptyList()) }
                    val sibSummariesDef = list.filter { it.id != kidId }.map { sib ->
                        async {
                            FamilyMember(
                                sib,
                                runCatching { stars.summary(sib.id).availableStars }.getOrNull(),
                            )
                        }
                    }
                    State(
                        loading = false,
                        familyTz = tz,
                        self = self,
                        siblings = sibSummariesDef.awaitAll(),
                        weekAnchor = anchor,
                        days = daysDef.await(),
                        summary = summaryDef.await(),
                        newStars = seenDef.await()?.newStars.orEmpty(),
                        rewards = itemsDef.await(),
                    )
                }
            }.onSuccess { if (it != null) _state.value = it }
                .onFailure { _state.value = State(loading = false, error = errors.message(it)) }
        }
    }

    fun shiftWeek(deltaDays: Long) {
        val kidId = sessionStore.load()?.kidId ?: return
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

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            auth.logout()
            onDone()
        }
    }
}

@Composable
fun KidHomeScreen(
    onOpenSibling: (String) -> Unit,
    onSignedOut: () -> Unit,
    viewModel: KidHomeViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsState()
    var showSignOutDialog by remember { mutableStateOf(false) }

    Scaffold { padding ->
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
