package com.kochvaia.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Round avatar — colored circle with an emoji centered inside. Single source
 * of truth so every screen renders the same chip regardless of size.
 */
@Composable
fun KidAvatar(
    emoji: String,
    colorHex: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colorHex.toComposeColor(MaterialTheme.colorScheme.secondary)),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, style = textStyle)
    }
}
