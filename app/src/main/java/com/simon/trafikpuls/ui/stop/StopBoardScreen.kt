package com.simon.trafikpuls.ui.stop

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simon.trafikpuls.domain.model.Alert
import com.simon.trafikpuls.domain.model.BoardKind
import com.simon.trafikpuls.domain.model.TimeWindow
import com.simon.trafikpuls.domain.model.TimetableEntry
import com.simon.trafikpuls.ui.components.AttributionFooter
import com.simon.trafikpuls.ui.components.BannerTone
import com.simon.trafikpuls.ui.components.DepartureRow
import com.simon.trafikpuls.ui.components.EmptyState
import com.simon.trafikpuls.ui.components.ErrorState
import com.simon.trafikpuls.ui.components.HealthBadge
import com.simon.trafikpuls.ui.components.MessageBanner
import com.simon.trafikpuls.ui.components.OperatorFilterRow
import com.simon.trafikpuls.ui.components.PanelCard
import com.simon.trafikpuls.ui.components.freshnessText
import com.simon.trafikpuls.ui.format.Format
import com.simon.trafikpuls.ui.theme.TrafikpulsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopBoardScreen(
    areaId: String,
    name: String,
    onBack: () -> Unit,
    onOpenTrip: (TimetableEntry) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: StopBoardViewModel = viewModel(factory = StopBoardViewModel.factory(areaId, name)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLineFilter by rememberSaveable { mutableStateOf(false) }

    if (showLineFilter) {
        LineFilterSheet(
            options = state.lineOptions,
            onSave = { keys ->
                viewModel.setLineFilter(keys)
                showLineFilter = false
            },
            onDismiss = { showLineFilter = false },
        )
    }

    LifecycleStartEffect(Unit) {
        viewModel.startAutoRefresh()
        onStopOrDispose { viewModel.stopAutoRefresh() }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tillbaka")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleWatch) {
                        if (state.isWatched) {
                            Icon(Icons.Filled.Star, contentDescription = "Sluta bevaka", tint = TrafikpulsTheme.colors.warning)
                        } else {
                            Icon(Icons.Outlined.StarOutline, contentDescription = "Bevaka hållplatsen")
                        }
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
            val error = state.error
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item(key = "controls") {
                    BoardControls(
                        kind = state.query.kind,
                        window = state.query.window,
                        onKind = viewModel::setKind,
                        onWindow = viewModel::setWindow,
                    )
                }
                item(key = "lines") {
                    LineFilterChip(
                        followed = state.lineFilter.size,
                        enabled = state.hasBoard,
                        onClick = { showLineFilter = true },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (state.operatorOptions.isNotEmpty() || state.selectedOperator != null) {
                    item(key = "operators") {
                        OperatorFilterRow(
                            options = state.operatorOptions,
                            selected = state.selectedOperator,
                            onSelect = viewModel::selectOperator,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                when {
                    state.isLoading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    !state.hasBoard && error != null -> item(key = "error") {
                        ErrorState(error = error, onRetry = viewModel::refresh, onOpenSettings = onOpenSettings)
                    }

                    else -> {
                        if (error != null) {
                            item(key = "stale") {
                                MessageBanner(
                                    text = "Kunde inte uppdatera: ${Format.errorShort(error)}. Visar senast hämtade data.",
                                    tone = BannerTone.WARNING,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                        }
                        item(key = "summary") { BoardSummary(state) }
                        if (state.stopAlerts.isNotEmpty()) {
                            item(key = "alerts") { StopAlerts(state.stopAlerts) }
                        }
                        if (state.entries.isEmpty()) {
                            item(key = "empty") {
                                EmptyState(
                                    icon = Icons.Filled.EventBusy,
                                    title = "Inga ${state.query.kind.label.lowercase()}",
                                    message = if (state.lineFilter.isNotEmpty()) {
                                        "Inga ${state.query.kind.label.lowercase()} för dina valda linjer under perioden."
                                    } else if (state.selectedOperator != null) {
                                        "Inga ${state.query.kind.label.lowercase()} för ${state.selectedOperator} under perioden."
                                    } else {
                                        "Inga ${state.query.kind.label.lowercase()} under perioden."
                                    },
                                )
                            }
                        } else {
                            item(key = "list") {
                                PanelCard(
                                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                                    contentPadding = PaddingValues(vertical = 4.dp),
                                ) {
                                    state.entries.forEachIndexed { index, entry ->
                                        if (index > 0) HorizontalDivider(color = TrafikpulsTheme.colors.panelBorder)
                                        DepartureRow(entry = entry, policy = state.policy, onClick = { onOpenTrip(entry) })
                                    }
                                }
                            }
                        }
                        item(key = "footer") {
                            AttributionFooter(isDemo = state.isDemo, freshness = freshnessText(state.meta))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardControls(
    kind: BoardKind,
    window: TimeWindow,
    onKind: (BoardKind) -> Unit,
    onWindow: (TimeWindow) -> Unit,
) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            BoardKind.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == kind,
                    onClick = { onKind(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, BoardKind.entries.size),
                    label = { Text(option.label) },
                )
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TimeWindow.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == window,
                    onClick = { onWindow(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, TimeWindow.entries.size),
                    label = { Text(if (option == TimeWindow.UPCOMING) "Prognos ${option.label.lowercase()}" else "Utfall ${option.label.lowercase()}") },
                )
            }
        }
    }
}

@Composable
private fun BoardSummary(state: StopBoardUiState) {
    val kpis = state.kpis
    val colors = TrafikpulsTheme.colors
    PanelCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Punktlighet ${Format.percent(kpis.punctuality)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            HealthBadge(state.health)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SummaryStat("Avgångar", Format.count(kpis.total))
            SummaryStat("Försenade", Format.count(kpis.late))
            SummaryStat("Tidiga", Format.count(kpis.early))
            SummaryStat("Inställda", Format.count(kpis.canceled))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Medelförsening ${Format.averageDelay(kpis.averageDelaySeconds)} · " +
                "realtidstäckning ${Format.percent(kpis.realtimeCoverage)}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkSecondary,
        )
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TrafikpulsTheme.colors.inkSecondary)
    }
}

@Composable
private fun StopAlerts(alerts: List<Alert>) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        alerts.forEach { alert ->
            var expanded by rememberSaveable(alert.dedupeKey) { mutableStateOf(false) }
            var truncated by remember(alert.dedupeKey) { mutableStateOf(false) }
            PanelCard(Modifier.fillMaxWidth().animateContentSize(), onClick = { expanded = !expanded }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Campaign, contentDescription = null, tint = TrafikpulsTheme.colors.warning, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(alert.typeLabel, style = MaterialTheme.typography.labelMedium, color = TrafikpulsTheme.colors.inkSecondary)
                }
                Spacer(Modifier.height(4.dp))
                Text(alert.title, style = MaterialTheme.typography.titleSmall)
                if (alert.text.isNotBlank() && alert.text != alert.title) {
                    Text(
                        alert.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = TrafikpulsTheme.colors.inkSecondary,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { layout -> if (!expanded) truncated = layout.hasVisualOverflow },
                    )
                    if (truncated && !expanded) {
                        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(0.dp)) { Text("Visa mer") }
                    }
                }
            }
        }
    }
}
