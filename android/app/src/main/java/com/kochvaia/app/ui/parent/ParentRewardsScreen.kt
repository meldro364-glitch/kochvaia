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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kochvaia.app.data.remote.ApiErrorAdapter
import com.kochvaia.app.data.remote.ItemDto
import com.kochvaia.app.data.repo.ItemRepository
import com.kochvaia.app.ui.theme.StarAmber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ParentRewardsViewModel @Inject constructor(
    private val items: ItemRepository,
    private val errors: ApiErrorAdapter,
) : ViewModel() {
    sealed interface UiState {
        data object Loading : UiState
        data class Ready(val items: List<ItemDto>) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching { items.list() }
                .onSuccess { _state.value = UiState.Ready(it) }
                .onFailure { _state.value = UiState.Error(errors.message(it)) }
        }
    }

    fun add(name: String, costStars: Int, emoji: String, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { items.add(name, costStars, emoji) }
                .onSuccess { refresh(); onDone() }
                .onFailure { _state.value = UiState.Error(errors.message(it)) }
        }
    }

    fun edit(id: String, name: String, costStars: Int, emoji: String, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { items.edit(id, name, costStars, emoji) }
                .onSuccess { refresh(); onDone() }
                .onFailure { _state.value = UiState.Error(errors.message(it)) }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { items.delete(id) }
                .onSuccess { refresh() }
                .onFailure { _state.value = UiState.Error(errors.message(it)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentRewardsScreen(
    onBack: () -> Unit,
    viewModel: ParentRewardsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showEditor by remember { mutableStateOf<ItemDto?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ItemDto?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rewards") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add reward")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                ParentRewardsViewModel.UiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ParentRewardsViewModel.UiState.Error -> Text(
                    "Couldn't load rewards: ${s.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                is ParentRewardsViewModel.UiState.Ready -> {
                    if (s.items.isEmpty()) {
                        EmptyRewardsHint(onAdd = { showAdd = true }, modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item { Spacer(Modifier.height(8.dp)) }
                            items(s.items, key = { it.id }) { item ->
                                ParentItemCard(
                                    item = item,
                                    onEdit = { showEditor = item },
                                    onDelete = { confirmDelete = item },
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
        ItemEditorDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { name, cost, emoji ->
                viewModel.add(name, cost, emoji) { showAdd = false }
            },
        )
    }
    showEditor?.let { item ->
        ItemEditorDialog(
            initial = item,
            onDismiss = { showEditor = null },
            onSave = { name, cost, emoji ->
                viewModel.edit(item.id, name, cost, emoji) { showEditor = null }
            },
        )
    }
    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${item.name}?") },
            text = { Text("Kids will no longer see this reward.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(item.id)
                    confirmDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ParentItemCard(
    item: ItemDto,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(StarAmber.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.emoji, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${item.costStars} ⭐",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun EmptyRewardsHint(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎁", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(8.dp))
        Text("No rewards yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Add rewards kids can save up for.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onAdd) { Text("Add a reward") }
    }
}

@Composable
private fun ItemEditorDialog(
    initial: ItemDto?,
    onDismiss: () -> Unit,
    onSave: (name: String, costStars: Int, emoji: String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var costText by remember { mutableStateOf(initial?.costStars?.toString() ?: "5") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "🎁") }
    val emojis = listOf("🎁", "🍕", "🍦", "🎬", "🎮", "🚴", "📚", "🍰", "🎨", "🏖️")
    val parsedCost = costText.toIntOrNull()
    val canSave = name.isNotBlank() && parsedCost != null && parsedCost in 1..10_000

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add reward" else "Edit reward") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it.filter(Char::isDigit).take(5) },
                    label = { Text("Cost in stars") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), parsedCost!!, emoji) },
                enabled = canSave,
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
