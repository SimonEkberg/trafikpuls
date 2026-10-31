package com.simon.trafikpuls.ui.alerts

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simon.trafikpuls.ui.components.AttributionFooter
import com.simon.trafikpuls.ui.components.BannerTone
import com.simon.trafikpuls.ui.components.EmptyState
import com.simon.trafikpuls.ui.components.LineBadge
import com.simon.trafikpuls.ui.components.MessageBanner
import com.simon.trafikpuls.ui.components.OperatorFilterRow
import com.simon.trafikpuls.ui.components.PanelCard
import com.simon.trafikpuls.ui.format.Format
import com.simon.trafikpuls.ui.theme.TrafikpulsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    onOpenStop: (areaId: String, name: String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: AlertsViewModel = viewModel(factory = AlertsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleStartEffect(Unit) {
        viewModel.startAutoRefresh()
        onStopOrDispose { viewModel.stopAutoRefresh() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Störningar") },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isRefreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Uppdatera")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.isDemo) {
                    item(key = "demo") {
                        MessageBanner(
                            text = "Demoläge: meddelandena är fiktiva.",
                            tone = BannerTone.INFO,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            actionLabel = "Ändra",
                            onAction = onOpenSettings,
                        )
                    }
                }
                when {
                    !state.isInitialized -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    state.watchlistEmpty -> item(key = "empty-watchlist") {
                        EmptyState(
                            icon = Icons.Outlined.Campaign,
                            title = "Inga bevakade hållplatser",
                            message = "Trafikmeddelanden visas för de hållplatser du bevakar i översikten.",
                        )
                    }

                    else -> {
                        if (state.operatorOptions.isNotEmpty() || state.selectedOperator != null) {
                            item(key = "operators") {
                                OperatorFilterRow(
                                    options = state.operatorOptions,
                                    selected = state.selectedOperator,
                                    onSelect = viewModel::selectOperator,
                                )
                            }
                        }
                        if (state.groups.isEmpty()) {
                            item(key = "none") {
                                EmptyState(
                                    icon = Icons.Filled.CheckCircle,
                                    title = "Inga trafikmeddelanden",
                                    message = "Det finns inga aktiva meddelanden för de bevakade hållplatserna i urvalet.",
                                )
                            }
                        }
                        items(state.groups, key = { it.alert.dedupeKey }) { group ->
                            AlertCard(group, onOpenStop, Modifier.padding(horizontal = 16.dp))
                        }
                        item(key = "footer") {
                            AttributionFooter(
                                isDemo = state.isDemo,
                                freshness = state.lastUpdated?.let { "Senast kontrollerad ${Format.clock(it)}" },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun alertIcon(type: String): ImageVector = when (type.uppercase()) {
    "CONSTRUCTION" -> Icons.Filled.Construction
    "MAINTENANCE" -> Icons.Filled.Build
    "TECHNICAL_PROBLEM" -> Icons.Filled.ReportProblem
    "ACCIDENT" -> Icons.Filled.Warning
    "WEATHER" -> Icons.Filled.Thunderstorm
    "POLICE_ACTIVITY" -> Icons.Filled.LocalPolice
    "MEDICAL_EMERGENCY" -> Icons.Filled.MedicalServices
    "STRIKE", "DEMONSTRATION" -> Icons.Filled.Groups
    "HOLIDAY" -> Icons.Filled.Event
    else -> Icons.Outlined.Campaign
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertCard(
    group: AlertGroupUi,
    onOpenStop: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TrafikpulsTheme.colors
    var expanded by rememberSaveable(group.alert.dedupeKey) { mutableStateOf(false) }
    PanelCard(modifier.fillMaxWidth().animateContentSize(), onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(alertIcon(group.alert.type), contentDescription = null, tint = colors.warning, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(group.alert.typeLabel, style = MaterialTheme.typography.labelMedium, color = colors.inkSecondary, modifier = Modifier.weight(1f))
            Text(
                when {
                    group.affectedDepartures > 0 -> "${group.affectedDepartures} avgångar"
                    group.isStopLevel -> "Hållplatsen"
                    else -> ""
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkSecondary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(group.alert.title, style = MaterialTheme.typography.titleSmall)
        if (group.alert.text.isNotBlank() && group.alert.text != group.alert.title) {
            Spacer(Modifier.height(2.dp))
            Text(
                group.alert.text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (group.lines.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                group.lines.forEach { LineBadge(it) }
            }
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            group.stops.forEach { stop ->
                AssistChip(
                    onClick = { onOpenStop(stop.areaId, stop.name) },
                    label = { Text(stop.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
        }
    }
}
