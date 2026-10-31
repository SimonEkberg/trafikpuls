package com.simon.trafikpuls.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simon.trafikpuls.domain.DepartureStatus
import com.simon.trafikpuls.domain.HealthStatus
import com.simon.trafikpuls.domain.model.Line
import com.simon.trafikpuls.domain.model.TransportMode
import com.simon.trafikpuls.ui.format.Format
import com.simon.trafikpuls.ui.theme.TrafikpulsTheme

fun TransportMode.icon(): ImageVector = when (this) {
    TransportMode.BUS -> Icons.Filled.DirectionsBus
    TransportMode.TRAIN -> Icons.Filled.Train
    TransportMode.METRO -> Icons.Filled.Subway
    TransportMode.TRAM -> Icons.Filled.Tram
    TransportMode.BOAT -> Icons.Filled.DirectionsBoat
    TransportMode.TAXI -> Icons.Filled.LocalTaxi
    TransportMode.UNKNOWN -> Icons.Filled.Commute
}

private val Ink = Color(0xFF0B0B0B)

/** White or ink, whichever has the higher contrast on [background]. */
private fun readableOn(background: Color): Color {
    val luminance = background.luminance()
    val contrastWithWhite = 1.05f / (luminance + 0.05f)
    val contrastWithInk = (luminance + 0.05f) / (Ink.luminance() + 0.05f)
    return if (contrastWithInk >= contrastWithWhite) Ink else Color.White
}

/** Line number on the transport mode's color, with the mode icon so color is never the only cue. */
@Composable
fun LineBadge(line: Line, modifier: Modifier = Modifier) {
    val background = TrafikpulsTheme.colors.forMode(line.mode)
    val content = readableOn(background)
    Row(
        modifier = modifier
            .semantics { contentDescription = Format.lineLabel(line) }
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .widthIn(min = 48.dp, max = 104.dp)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(line.mode.icon(), contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            text = line.designation,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

data class StatusVisual(val icon: ImageVector, val tint: Color)

@Composable
fun DepartureStatus.visual(): StatusVisual {
    val colors = TrafikpulsTheme.colors
    return when (this) {
        DepartureStatus.CANCELED -> StatusVisual(Icons.Filled.Cancel, colors.critical)
        DepartureStatus.SEVERELY_LATE -> StatusVisual(Icons.Filled.Warning, colors.critical)
        DepartureStatus.LATE -> StatusVisual(Icons.Filled.Schedule, colors.serious)
        DepartureStatus.EARLY -> StatusVisual(Icons.Filled.FastForward, colors.warning)
        DepartureStatus.ON_TIME -> StatusVisual(Icons.Filled.CheckCircle, colors.good)
        DepartureStatus.NO_REALTIME -> StatusVisual(Icons.Outlined.Schedule, colors.inkMuted)
    }
}

/** Status pill: colored icon + text in ink - the text never takes the status color. */
@Composable
fun DelayBadge(status: DepartureStatus, delaySeconds: Int, modifier: Modifier = Modifier) {
    val visual = status.visual()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(visual.icon, contentDescription = null, tint = visual.tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = Format.statusLabel(status, delaySeconds),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
fun HealthStatus.label(): String = when (this) {
    HealthStatus.OK -> "Normal drift"
    HealthStatus.WARNING -> "Avvikelser"
    HealthStatus.CRITICAL -> "Störd trafik"
    HealthStatus.UNKNOWN -> "Inget underlag"
}

@Composable
fun HealthBadge(status: HealthStatus, modifier: Modifier = Modifier) {
    val colors = TrafikpulsTheme.colors
    val (icon, tint) = when (status) {
        HealthStatus.OK -> Icons.Filled.CheckCircle to colors.good
        HealthStatus.WARNING -> Icons.Filled.Warning to colors.warning
        HealthStatus.CRITICAL -> Icons.Filled.Error to colors.critical
        HealthStatus.UNKNOWN -> Icons.Outlined.Info to colors.inkMuted
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(status.label(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
