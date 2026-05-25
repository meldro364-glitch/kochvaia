package com.kochvaia.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kochvaia.app.data.remote.DayDto
import com.kochvaia.app.ui.theme.StarAmber
import com.kochvaia.app.ui.theme.UsedGray
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Renders one row of N day-cells with status (none/given/used). Optional
 * onTap is called with the YYYY-MM-DD date string of the tapped cell.
 */
@Composable
fun DayStrip(
    days: List<DayDto>,
    modifier: Modifier = Modifier,
    onTap: ((String) -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        days.forEach { day ->
            DayCell(
                day = day,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp),
                onTap = if (onTap != null) ({ onTap(day.date) }) else null,
            )
        }
    }
}

@Composable
fun DayCell(
    day: DayDto,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
) {
    val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
    val weekday = date?.format(DateTimeFormatter.ofPattern("EEE"))?.uppercase() ?: ""
    val dayOfMonth = date?.dayOfMonth?.toString() ?: ""

    Column(
        modifier = modifier
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            weekday,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(cellBackground(day.status))
                .border(
                    width = if (day.status == "none") 1.dp else 0.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (day.status) {
                "given" -> Text("⭐", style = MaterialTheme.typography.titleMedium)
                "used" -> Text("✦", color = Color.White, style = MaterialTheme.typography.titleMedium)
                else -> Text(
                    dayOfMonth,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun cellBackground(status: String): Color = when (status) {
    "given" -> StarAmber.copy(alpha = 0.25f)
    "used" -> UsedGray
    else -> Color.Transparent
}
