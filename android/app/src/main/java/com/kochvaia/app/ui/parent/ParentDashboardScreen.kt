package com.kochvaia.app.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kochvaia.app.data.cache.DashboardLoader
import com.kochvaia.app.data.cache.KidsCache
import com.kochvaia.app.data.cache.SummariesCache
import com.kochvaia.app.data.remote.ApiErrorAdapter
import com.kochvaia.app.data.remote.KidDto
import com.kochvaia.app.data.repo.AuthRepository
import com.kochvaia.app.data.repo.KidRepository
import com.kochvaia.app.ui.common.KidAvatar
import com.kochvaia.app.ui.common.toComposeColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KidRow(val kid: KidDto, val availableStars: Int)

@HiltViewModel
class ParentDashboardViewModel @Inject constructor(
    private val kidsRepo: KidRepository,
    private val auth: AuthRepository,
    private val dashboard: DashboardLoader,
    private val kidsCache: KidsCache,
    private val summariesCache: SummariesCache,
    private val errors: ApiErrorAdapter,
) : ViewModel() {
    sealed interface UiState {
        data object Loading : UiState
        data class Ready(val rows: List<KidRow>) : UiState
        data class Error(val message: String) : UiState
    }

    /**
     * Renders from the cached kids list + per-kid summaries immediately —
     * the screen never blocks on the network when there's a snapshot from
     * a previous session. `refresh()` runs a /dashboard fetch in the
     * background; on success the flow re-emits with fresh data and the UI
     * silently updates. On failure we keep the cached rows visible.
     */
    private val _errorOverride = MutableStateFlow<String?>(null)
    val state: StateFlow<UiState> = combine(
        kidsCache.flow,
        summariesCache.flow,
        _errorOverride,
    ) { kids, summaries, err ->
        when {
            kids == null && err != null -> UiState.Error(err)
            kids == null -> UiState.Loading
            else -> UiState.Ready(
                kids.map { k ->
                    KidRow(k, summaries[k.id]?.availableStars ?: 0)
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun refresh() {
        viewModelScope.launch {
            dashboard.refresh()
                .onSuccess { _errorOverride.value = null }
                .onFailure { _errorOverride.value = errors.message(it) }
        }
    }

    fun addKid(name: String, emoji: String, color: String, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { kidsRepo.add(name, emoji, color) }
                .onSuccess { newKid ->
                    // Optimistically append; refresh confirms.
                    kidsCache.flow.value?.let { kidsCache.put(it + newKid) }
                    refresh()
                    onDone()
                }
                .onFailure { _errorOverride.value = errors.message(it) }
        }
    }

    fun rename(kidId: String, newName: String) {
        viewModelScope.launch {
            runCatching { kidsRepo.rename(kidId, newName) }
                .onSuccess {
                    // Optimistic rename in cache.
                    kidsCache.flow.value?.let { list ->
                        kidsCache.put(list.map { if (it.id == kidId) it.copy(displayName = newName) else it })
                    }
                    refresh()
                }
                .onFailure { _errorOverride.value = errors.message(it) }
        }
    }

    fun delete(kidId: String) {
        viewModelScope.launch {
            runCatching { kidsRepo.delete(kidId) }
                .onSuccess {
                    kidsCache.flow.value?.let { list ->
                        kidsCache.put(list.filterNot { it.id == kidId })
                    }
                    refresh()
                }
                .onFailure { _errorOverride.value = errors.message(it) }
        }
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
fun ParentDashboardScreen(
    onOpenKid: (String) -> Unit,
    onShareQr: (String) -> Unit,
    onShareCoParent: () -> Unit,
    onOpenRewards: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: ParentDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }
    RequestNotificationPermissionOnce()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kids") },
                actions = {
                    IconButton(onClick = onOpenRewards) {
                        Icon(Icons.Filled.CardGiftcard, contentDescription = "Rewards")
                    }
                    IconButton(onClick = onShareCoParent) {
                        Icon(Icons.Filled.QrCode, contentDescription = "Invite co-parent")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Sign out") },
                            onClick = {
                                menuOpen = false
                                viewModel.signOut(onSignOut)
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add kid")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                ParentDashboardViewModel.UiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ParentDashboardViewModel.UiState.Error -> Text(
                    "Couldn't load kids: ${s.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                is ParentDashboardViewModel.UiState.Ready -> {
                    if (s.rows.isEmpty()) {
                        EmptyKidsHint(onAdd = { showAdd = true }, modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item { Spacer(Modifier.height(8.dp)) }
                            items(s.rows, key = { it.kid.id }) { row ->
                                KidCard(
                                    row = row,
                                    onOpen = { onOpenKid(row.kid.id) },
                                    onShareQr = { onShareQr(row.kid.id) },
                                    onRename = { newName -> viewModel.rename(row.kid.id, newName) },
                                    onDelete = { viewModel.delete(row.kid.id) },
                                )
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddKidDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, emoji, color ->
                viewModel.addKid(name, emoji, color) { showAdd = false }
            },
        )
    }
}

@Composable
private fun KidCard(
    row: KidRow,
    onOpen: () -> Unit,
    onShareQr: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KidAvatar(
                emoji = row.kid.avatarEmoji,
                colorHex = row.kid.avatarColor,
                size = 56.dp,
                textStyle = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    row.kid.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${row.availableStars} star${if (row.availableStars == 1) "" else "s"} available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onShareQr) {
                Icon(Icons.Filled.QrCode, contentDescription = "Pair device")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { menuOpen = false; renaming = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menuOpen = false; confirmingDelete = true },
                    )
                }
            }
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(row.kid.displayName) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(name.trim())
                    renaming = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete ${row.kid.displayName}?") },
            text = { Text("Their stars will be hidden but the data won't be erased.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirmingDelete = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyKidsHint(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("⭐", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(8.dp))
        Text("No kids yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Add a kid to start awarding stars.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onAdd) { Text("Add a kid") }
    }
}

@Composable
private fun AddKidDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, emoji: String, color: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("⭐") }
    var color by remember { mutableStateOf("#FFB347") }
    val palette = listOf("#FFB347", "#9FD8B7", "#8DBEE0", "#F4A6C0", "#C7B8EA", "#F2D06B")
    val emojis = listOf("⭐", "🦄", "🐯", "🐼", "🦊", "🐸", "🦖", "🐙", "🌈", "🎈")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a kid") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Text("Emoji", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    emojis.take(6).forEach { e ->
                        TextButton(onClick = { emoji = e }, modifier = Modifier.size(40.dp)) {
                            Text(e, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Color", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    palette.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(c.toComposeColor(MaterialTheme.colorScheme.secondary))
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), emoji, color) },
                enabled = name.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Asks the user for POST_NOTIFICATIONS once per dashboard mount. Android 12
 * and below grant the permission implicitly so this composable is a no-op
 * there. A denial is silent — we just won't post reminders.
 */
@Composable
private fun RequestNotificationPermissionOnce() {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { /* result ignored — silent denial is fine */ }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}
