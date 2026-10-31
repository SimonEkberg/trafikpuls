package com.simon.trafikpuls.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.simon.trafikpuls.core.AppError
import com.simon.trafikpuls.core.DataOrigin
import com.simon.trafikpuls.core.FetchMeta
import com.simon.trafikpuls.ui.format.Format
import com.simon.trafikpuls.ui.theme.TrafikpulsTheme

/** Card on the chart surface with a hairline border - the container for every panel. */
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = TrafikpulsTheme.colors.panel)
    val border = BorderStroke(1.dp, TrafikpulsTheme.colors.panelBorder)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, colors = colors, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(modifier = modifier, colors = colors, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** Stat tile: label, value (semibold, proportional figures), optional supporting line. */
@Composable
fun KpiTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    iconTint: androidx.compose.ui.graphics.Color = TrafikpulsTheme.colors.inkMuted,
) {
    PanelCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TrafikpulsTheme.colors.inkSecondary)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        if (supporting != null) {
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = TrafikpulsTheme.colors.inkMuted)
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

enum class BannerTone { INFO, WARNING, ERROR }

@Composable
fun MessageBanner(
    text: String,
    tone: BannerTone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val container = when (tone) {
        BannerTone.INFO -> scheme.primaryContainer
        BannerTone.WARNING -> scheme.surfaceContainerHigh
        BannerTone.ERROR -> scheme.errorContainer
    }
    val content = when (tone) {
        BannerTone.INFO -> scheme.onPrimaryContainer
        BannerTone.WARNING -> scheme.onSurface
        BannerTone.ERROR -> scheme.onErrorContainer
    }
    val (icon, iconTint) = when (tone) {
        BannerTone.INFO -> Icons.Outlined.Info to scheme.onPrimaryContainer
        BannerTone.WARNING -> Icons.Filled.Warning to TrafikpulsTheme.colors.warning
        BannerTone.ERROR -> Icons.Filled.Error to TrafikpulsTheme.colors.critical
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = TrafikpulsTheme.colors.inkSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

/** Full error state with the actions that can actually fix the error. */
@Composable
fun ErrorState(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
) {
    val keyProblem = error == AppError.MissingApiKey || error == AppError.InvalidApiKey
    EmptyState(
        icon = when {
            keyProblem -> Icons.Filled.Key
            error is AppError.Network -> Icons.Filled.CloudOff
            else -> Icons.Filled.Error
        },
        title = if (keyProblem) "API-nyckel behövs" else "Kunde inte hämta trafikdata",
        message = Format.errorMessage(error),
        modifier = modifier,
    ) {
        if (keyProblem && onOpenSettings != null) {
            Button(onClick = onOpenSettings) { Text("Inställningar") }
            OutlinedButton(onClick = onRetry) { Text("Försök igen") }
        } else {
            Button(onClick = onRetry) { Text("Försök igen") }
            if (onOpenSettings != null) OutlinedButton(onClick = onOpenSettings) { Text("Inställningar") }
        }
    }
}

/** "Uppdaterad 14:32:05", or a clear note when the data is demo data or a stale fallback. */
fun freshnessText(meta: FetchMeta?): String? = when {
    meta == null -> null
    meta.isStale -> "Sparad data från ${Format.clock(meta.fetchedAt)} (${Format.errorShort(meta.staleReason)})"
    meta.origin == DataOrigin.DEMO -> "Demodata genererad ${Format.clock(meta.fetchedAt)}"
    else -> "Uppdaterad ${Format.clock(meta.fetchedAt)}"
}

/** CC BY 4.0 requires attribution wherever Trafiklab data is shown. */
@Composable
fun AttributionFooter(isDemo: Boolean, modifier: Modifier = Modifier, freshness: String? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (freshness != null) {
            Text(freshness, style = MaterialTheme.typography.bodySmall, color = TrafikpulsTheme.colors.inkMuted)
        }
        Text(
            if (isDemo) "Demodata – fiktiv trafik. Livedata: Trafiklab.se (CC BY 4.0)" else "Data: Trafiklab.se (CC BY 4.0)",
            style = MaterialTheme.typography.bodySmall,
            color = TrafikpulsTheme.colors.inkMuted,
            textAlign = TextAlign.Center,
        )
    }
}
