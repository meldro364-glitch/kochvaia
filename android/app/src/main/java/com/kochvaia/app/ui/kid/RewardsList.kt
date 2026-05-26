package com.kochvaia.app.ui.kid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kochvaia.app.data.remote.ItemDto
import com.kochvaia.app.ui.theme.StarAmber

/**
 * Vertical list of reward items, showing affordability against [availableStars].
 * Items the kid can afford render at full opacity with their cost; ones they
 * can't show a "Need N more ⭐" badge and dim the card.
 */
@Composable
fun RewardsList(
    rewards: List<ItemDto>,
    availableStars: Int,
    modifier: Modifier = Modifier,
) {
    if (rewards.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rewards.forEach { item ->
            RewardRow(item = item, availableStars = availableStars)
        }
    }
}

@Composable
private fun RewardRow(item: ItemDto, availableStars: Int) {
    val affordable = availableStars >= item.costStars
    val shortBy = (item.costStars - availableStars).coerceAtLeast(0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (affordable) 0.45f else 0.25f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .alpha(if (affordable) 1f else 0.6f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(StarAmber.copy(alpha = if (affordable) 0.35f else 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(item.emoji, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (affordable) "You can get it now!"
                else "Need $shortBy more ⭐",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${item.costStars} ⭐",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (affordable) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
