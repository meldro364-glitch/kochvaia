package com.kochvaia.app.ui.kid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kochvaia.app.data.remote.DayDto
import com.kochvaia.app.data.remote.KidDto
import com.kochvaia.app.data.remote.SummaryResponse
import com.kochvaia.app.ui.common.DayStrip
import com.kochvaia.app.ui.common.KidAvatar
import com.kochvaia.app.ui.common.WeekHeader
import java.time.LocalDate

/**
 * The big top section of any kid profile screen — avatar, name, big star
 * count, 7-day strip with week-back navigation. Shared by KidHomeScreen and
 * KidSiblingScreen.
 */
@Composable
fun KidProfileBlock(
    kid: KidDto?,
    summary: SummaryResponse?,
    days: List<DayDto>,
    weekAnchor: LocalDate,
    canGoNext: Boolean,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (kid != null) {
            KidAvatar(
                emoji = kid.avatarEmoji,
                colorHex = kid.avatarColor,
                size = 96.dp,
                textStyle = MaterialTheme.typography.displayMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                kid.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            "${summary?.availableStars ?: 0} ⭐",
            style = MaterialTheme.typography.displayLarge,
        )
        Text(
            "available right now",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        WeekHeader(anchor = weekAnchor, canGoNext = canGoNext, onPrev = onPrevWeek, onNext = onNextWeek)
        Spacer(Modifier.height(8.dp))
        DayStrip(days = days)
    }
}

/** Compact sibling chip used in the horizontal row on KidHomeScreen. */
@Composable
fun SiblingChip(
    kid: KidDto,
    availableStars: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KidAvatar(emoji = kid.avatarEmoji, colorHex = kid.avatarColor, size = 40.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(kid.displayName, style = MaterialTheme.typography.labelLarge)
            availableStars?.let {
                Text("$it ⭐", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
