package com.kochvaia.app.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fires [onHold] only if the pointer stays down for at least [holdMs].
 * The system long-press default (~500 ms) is too easy to trip accidentally
 * — we use this for "hidden" actions like signing a kid out.
 *
 * Uses withTimeoutOrNull (not withTimeout) so we don't throw a
 * CancellationException inside the gesture coroutine — that would cancel
 * the parent scope and kill further gesture detection.
 */
suspend fun PointerInputScope.detectLongHold(
    holdMs: Long,
    onHold: () -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val released = withTimeoutOrNull(holdMs) {
            waitForUpOrCancellation()
        }
        if (released == null) onHold()
    }
}
