package com.kochvaia.app.ui.common

import androidx.compose.ui.graphics.Color

/**
 * Parses a "#RRGGBB" hex string into a Compose [Color]. Returns [fallback] if
 * the input is null or malformed — callers shouldn't have to know that
 * `android.graphics.Color.parseColor` throws on bad input.
 */
fun String?.toComposeColor(fallback: Color): Color {
    if (this == null) return fallback
    return runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrDefault(fallback)
}
