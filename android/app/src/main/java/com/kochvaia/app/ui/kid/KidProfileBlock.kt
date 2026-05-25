package com.kochvaia.app.ui.kid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kochvaia.app.data.remote.DayDto
import com.kochvaia.app.data.remote.KidDto
import com.kochvaia.app.data.remote.SummaryResponse
import com.kochvaia.app.ui.common.DayStrip
import com.kochvaia.app.ui.common.KidAvatar
import com.kochvaia.app.ui.common.WeekHeader
import java.time.LocalDate

/**
 * One entry in the "Family" row shown on kid screens. The chip may represent
 * the paired-as kid (when viewing a sibling) — that's why we use this name
 * over "Sibling".
 */
data class FamilyMember(val kid: KidDto, val availableStars: Int?)

/**
 * Two-column layout in landscape (>= 600.dp wide), single column otherwise.
 * Both halves get their own vertical scroll so they each fit independently.
 *
 * @param header receives `isWide` so callers can shrink the avatar/font when
 *   they only have ~45% of screen width to work with.
 */
@Composable
fun KidProfileLayout(
    modifier: Modifier = Modifier,
    header: @Composable (isWide: Boolean) -> Unit,
    body: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.4f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    header(true)
                }
                Column(
                    modifier = Modifier
                        .weight(0.6f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    body()
                    Spacer(Modifier.height(24.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                header(false)
                Spacer(Modifier.height(24.dp))
                Column(modifier = Modifier.fillMaxWidth()) { body() }
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

/** Avatar + name + big star count. Top half of any kid profile. */
@Composable
fun KidProfileHeader(
    kid: KidDto?,
    summary: SummaryResponse?,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 96.dp,
    nameStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    starsStyle: TextStyle = MaterialTheme.typography.displayLarge,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (kid != null) {
            KidAvatar(
                emoji = kid.avatarEmoji,
                colorHex = kid.avatarColor,
                size = avatarSize,
                textStyle = MaterialTheme.typography.displayMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(kid.displayName, style = nameStyle, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
        }
        Text("${summary?.availableStars ?: 0} ⭐", style = starsStyle)
        Text(
            "available right now",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Week header (prev / range / next) + 7-day strip. Bottom half of any kid profile. */
@Composable
fun KidProfileWeek(
    days: List<DayDto>,
    weekAnchor: LocalDate,
    canGoNext: Boolean,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        WeekHeader(anchor = weekAnchor, canGoNext = canGoNext, onPrev = onPrevWeek, onNext = onNextWeek)
        Spacer(Modifier.height(8.dp))
        DayStrip(days = days)
    }
}

/** Compact sibling chip used in the horizontal "Family" row. */
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
