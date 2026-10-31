package com.simon.trafikpuls.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simon.trafikpuls.domain.PunctualityPolicy
import com.simon.trafikpuls.domain.model.TimetableEntry
import com.simon.trafikpuls.ui.format.Format
import com.simon.trafikpuls.ui.theme.TrafikpulsTheme

/**
 * One departure: expected time (scheduled struck through when it differs), line, direction,
 * operator and platform, and the status pill. [contextLabel] replaces the operator line where
 * the stop matters more (e.g. the overview's deviation list).
 */
@Composable
fun DepartureRow(
    entry: TimetableEntry,
    policy: PunctualityPolicy,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contextLabel: String? = null,
) {
    val status = policy.classify(entry)
    val showScheduled = entry.isRealtime && !entry.isCanceled && Format.time(entry.expected) != Format.time(entry.scheduled)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(52.dp)) {
            Text(
                Format.time(if (entry.isCanceled) entry.scheduled else entry.expected),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (entry.isCanceled) TextDecoration.LineThrough else null,
            )
            if (showScheduled) {
                Text(
                    Format.time(entry.scheduled),
                    style = MaterialTheme.typography.bodySmall,
                    color = TrafikpulsTheme.colors.inkMuted,
                    textDecoration = TextDecoration.LineThrough,
                )
            }
        }
        LineBadge(entry.line)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.line.direction.ifBlank { entry.line.destinationName.orEmpty() },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (entry.isCanceled) TextDecoration.LineThrough else null,
            )
            Format.origin(entry)?.let { origin ->
                Text(
                    origin,
                    style = MaterialTheme.typography.bodySmall,
                    color = TrafikpulsTheme.colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val secondary = buildString {
                    append(contextLabel ?: entry.operatorLabel)
                    entry.platform?.let { append(" · Läge $it") }
                }
                Text(
                    secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = TrafikpulsTheme.colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.platformChanged) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = "Ändrat läge",
                        tint = TrafikpulsTheme.colors.warning,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "nytt läge",
                        style = MaterialTheme.typography.bodySmall,
                        color = TrafikpulsTheme.colors.inkSecondary,
                    )
                }
                if (entry.alerts.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Outlined.Campaign,
                        contentDescription = "Trafikmeddelande",
                        tint = TrafikpulsTheme.colors.inkSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        DelayBadge(status, entry.delaySeconds)
    }
}
