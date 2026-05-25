package com.kochvaia.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** A 7-day window ending on (and including) [anchor]. */
fun weekRange(anchor: LocalDate): Pair<LocalDate, LocalDate> = anchor.minusDays(6) to anchor

fun monthDay(d: LocalDate): String =
    "${d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${d.dayOfMonth}"

/** Prev / range chip / next row used by every screen that paginates by week. */
@Composable
fun WeekHeader(
    anchor: LocalDate,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous week")
        }
        val (from, to) = weekRange(anchor)
        AssistChip(onClick = {}, label = { Text("${monthDay(from)} – ${monthDay(to)}") })
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next week")
        }
    }
}
