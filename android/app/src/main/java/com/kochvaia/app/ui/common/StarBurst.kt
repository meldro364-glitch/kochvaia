package com.kochvaia.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kochvaia.app.data.remote.SeenStar
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Plays an arrival animation for each newly-awarded star: a large star drops
 * in from above, settles with a bounce, then fades. Stars play with a short
 * stagger so multiple feel like a "drip" rather than a chaotic burst.
 *
 * onFinished is called once the entire sequence has played.
 */
@Composable
fun StarBurstOverlay(
    stars: List<SeenStar>,
    modifier: Modifier = Modifier,
    perStarMs: Int = 900,
    staggerMs: Int = 350,
    onFinished: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        stars.forEachIndexed { idx, star ->
            FallingStar(
                key = star.id,
                delayMs = idx * staggerMs,
                durationMs = perStarMs,
            )
        }
        // Keyed on the id list so two consecutive bursts with different stars
        // restart the timer cleanly. Same-identity reruns are a no-op.
        val burstKey = remember(stars) { stars.joinToString(",") { it.id } }
        LaunchedEffect(burstKey) {
            val total = ((stars.size - 1) * staggerMs + perStarMs).toLong()
            delay(total)
            onFinished()
        }
    }
}

@Composable
private fun FallingStar(key: String, delayMs: Int, durationMs: Int) {
    val density = LocalDensity.current
    val travelPx = with(density) { 220.dp.toPx() }
    val translateY = remember(key) { Animatable(-travelPx) }
    val scale = remember(key) { Animatable(0.4f) }
    val opacity = remember(key) { Animatable(0f) }

    LaunchedEffect(key) {
        if (delayMs > 0) delay(delayMs.toLong())
        coroutineScope {
            launch {
                opacity.animateTo(1f, tween(180))
                delay((durationMs - 360).coerceAtLeast(0).toLong())
                opacity.animateTo(0f, tween(180))
            }
            launch {
                translateY.animateTo(0f, tween(durationMs - 200, easing = LinearOutSlowInEasing))
            }
            launch {
                scale.animateTo(1.2f, tween(durationMs / 2, easing = LinearOutSlowInEasing))
                scale.animateTo(1f, tween(durationMs / 2))
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, translateY.value.roundToInt()) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "⭐",
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer {
                    // Reading the animatables inside graphicsLayer defers them
                    // to the draw phase, skipping per-frame recomposition.
                    scaleX = scale.value
                    scaleY = scale.value
                    alpha = opacity.value
                },
            style = MaterialTheme.typography.displayLarge,
        )
    }
}
