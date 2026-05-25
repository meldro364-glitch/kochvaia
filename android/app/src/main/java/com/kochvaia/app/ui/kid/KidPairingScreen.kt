package com.kochvaia.app.ui.kid

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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
import java.util.concurrent.Executors
import javax.inject.Inject

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

    fun submit(rawCode: String) {
        val code = normalizeCode(rawCode) ?: run {
            _state.value = UiState.Error("invalid_code_format")
            return
        }
        _state.value = UiState.Pairing
        viewModelScope.launch {
            runCatching { auth.redeemKidQr(code, android.os.Build.MODEL) }
                .onSuccess { _state.value = UiState.Done }
                .onFailure { _state.value = UiState.Error(errors.message(it)) }
        }
    }

    fun resetError() { _state.value = UiState.Idle }

    /**
     * Accepts either a bare "ABCD-EFGH" code or a "kochvaia://join?code=..." URL.
     * Returns null if it doesn't match the expected shape.
     */
    private fun normalizeCode(raw: String): String? {
        val trimmed = raw.trim()
        val justCode = if (trimmed.startsWith("kochvaia://join")) {
            val idx = trimmed.indexOf("code=")
            if (idx < 0) return null
            trimmed.substring(idx + 5).substringBefore('&')
        } else trimmed
        val upper = justCode.uppercase()
        return if (Regex("^[0-9A-Z]{4}-?[0-9A-Z]{4}$").matches(upper)) {
            if (upper.contains('-')) upper else "${upper.substring(0, 4)}-${upper.substring(4)}"
        } else null
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
    val context = LocalContext.current
    var manualEntry by remember { mutableStateOf(false) }

    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val askCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPerm = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPerm) askCamera.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(state) {
        if (state is KidPairingViewModel.UiState.Done) onPaired()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (manualEntry) "Enter pairing code" else "Scan QR code") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { manualEntry = !manualEntry }) {
                        Text(if (manualEntry) "Use camera" else "Type code")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            when {
                manualEntry || !hasCameraPerm -> ManualEntry(
                    state = state,
                    cameraDenied = !hasCameraPerm && !manualEntry,
                    onSubmit = viewModel::submit,
                    onResetError = viewModel::resetError,
                )
                else -> CameraScanner(
                    state = state,
                    onCode = viewModel::submit,
                    onResetError = viewModel::resetError,
                )
            }
        }
    }
}

@Composable
private fun CameraScanner(
    state: KidPairingViewModel.UiState,
    onCode: (String) -> Unit,
    onResetError: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { QrBarcodeAnalyzer(onCode) }
    // Holds the bound provider so the dispose handler can unbind it. Without
    // this, the camera stays on after navigating away from the screen.
    val boundProvider = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            boundProvider.value?.unbindAll()
            analyzer.close()
            executor.shutdown()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val resolution = ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1280, 720),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build()
                        val analysis = ImageAnalysis.Builder()
                            .setResolutionSelector(resolution)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { it.setAnalyzer(executor, analyzer) }
                        runCatching {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                            boundProvider.value = provider
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
            )

            when (state) {
                KidPairingViewModel.UiState.Pairing -> Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text("Connecting…", color = Color.White)
                    }
                }
                is KidPairingViewModel.UiState.Error -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Couldn't connect: ${state.message}",
                            color = Color.White,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            analyzer.reset()
                            onResetError()
                        }) { Text("Try again") }
                    }
                }
                else -> Unit
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Point the camera at the QR code shown on a parent's phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ManualEntry(
    state: KidPairingViewModel.UiState,
    cameraDenied: Boolean,
    onSubmit: (String) -> Unit,
    onResetError: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        if (cameraDenied) {
            Text(
                "Camera access was denied — you can still type the code shown beneath the QR.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(16.dp))
        }
        OutlinedTextField(
            value = code,
            onValueChange = {
                code = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(9)
                if (state is KidPairingViewModel.UiState.Error) onResetError()
            },
            label = { Text("Code (e.g. ABCD-EFGH)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSubmit(code) },
            enabled = code.length >= 8 && state !is KidPairingViewModel.UiState.Pairing,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Connect") }
        Spacer(Modifier.height(16.dp))
        AnimatedVisibility(visible = state is KidPairingViewModel.UiState.Pairing) {
            CircularProgressIndicator()
        }
        if (state is KidPairingViewModel.UiState.Error) {
            Text(
                "Couldn't connect: ${state.message}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
