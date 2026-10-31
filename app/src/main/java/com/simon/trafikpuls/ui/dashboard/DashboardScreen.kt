package com.simon.trafikpuls.ui.dashboard

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simon.trafikpuls.data.repository.WatchlistRepository
import com.simon.trafikpuls.domain.model.TimetableEntry
import com.simon.trafikpuls.ui.components.AttributionFooter
import com.simon.trafikpuls.ui.components.BannerTone
import com.simon.trafikpuls.ui.components.DepartureRow
import com.simon.trafikpuls.ui.components.EmptyState
import com.simon.trafikpuls.ui.components.ErrorState
import com.simon.trafikpuls.ui.components.HealthBadge
import com.simon.trafikpuls.ui.components.KpiTile
import com.simon.trafikpuls.ui.components.MessageBanner
import com.simon.trafikpuls.ui.components.OperatorFilterRow
import com.simon.trafikpuls.ui.components.PanelCard
import com.simon.trafikpuls.ui.components.SectionHeader
import com.simon.trafikpuls.ui.components.icon
import com.simon.trafikpuls.ui.format.Format
import com.simon.trafikpuls.ui.theme.TrafikpulsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenStop: (areaId: String, name: String) -> Unit,
    onOpenTrip: (TimetableEntry) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
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
                title = {
                    Column {
                        Text("Trafikpuls", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (state.isDemo) "Driftläge · demodata" else "Driftläge · nästa 60 min",
                            style = MaterialTheme.typography.labelMedium,
                            color = TrafikpulsTheme.colors.inkSecondary,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isRefreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Uppdatera")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Inställningar")
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
            DashboardContent(
                state = state,
                onOpenStop = onOpenStop,
                onOpenTrip = onOpenTrip,
                onOpenSettings = onOpenSettings,
                onOpenSearch = onOpenSearch,
                onSelectOperator = viewModel::selectOperator,
                onRemoveStop = viewModel::removeStop,
                onAddSuggestions = viewModel::addSuggestions,
                onEnableDemo = viewModel::enableDemoMode,
                onRetry = viewModel::refresh,
            )
        }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    onOpenStop: (String, String) -> Unit,
    onOpenTrip: (TimetableEntry) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onSelectOperator: (String?) -> Unit,
    onRemoveStop: (String) -> Unit,
    onAddSuggestions: () -> Unit,
    onEnableDemo: () -> Unit,
    onRetry: () -> Unit,
) {
    val horizontal = Modifier.padding(horizontal = 16.dp)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.isDemo) {
            item(key = "demo") {
                MessageBanner(
                    text = "Demoläge: fiktiv trafik som genereras i appen. Lägg in en Trafiklab-nyckel för livedata.",
                    tone = BannerTone.INFO,
                    modifier = horizontal,
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

            state.needsApiKey -> item(key = "key") {
                EmptyState(
                    icon = Icons.Filled.Key,
                    title = "Lägg in en API-nyckel",
                    message = "Trafikpuls hämtar realtidsdata från Trafiklab. Skaffa en gratis nyckel för " +
                        "Trafiklab Realtime APIs på developer.trafiklab.se – eller utforska appen i demoläge.",
                ) {
                    Button(onClick = onOpenSettings) { Text("Ange nyckel") }
                    OutlinedButton(onClick = onEnableDemo) { Text("Demoläge") }
                }
            }

            state.watchlistEmpty -> item(key = "empty") {
                EmptyState(
                    icon = Icons.Filled.AddLocationAlt,
                    title = "Inga bevakade hållplatser",
                    message = "Lägg till de knutpunkter och terminaler du vill övervaka. " +
                        "Högst ${WatchlistRepository.MAX_WATCHED} hållplatser, så att uppdateringarna ryms i Trafiklabs kvot.",
                ) {
                    Button(onClick = onOpenSearch) { Text("Sök hållplats") }
                    OutlinedButton(onClick = onAddSuggestions) { Text("Lägg till förslag") }
                }
            }

            state.globalError != null -> item(key = "error") {
                ErrorState(error = state.globalError, onRetry = onRetry, onOpenSettings = onOpenSettings)
            }

            state.isFirstLoad -> item(key = "first-load") {
                Column(
                    Modifier.fillMaxWidth().padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Hämtar avgångar för ${state.watchedCount} hållplatser…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TrafikpulsTheme.colors.inkSecondary,
                    )
                }
            }

            else -> {
                if (state.operatorOptions.isNotEmpty() || state.selectedOperator != null) {
                    item(key = "operators") {
                        OperatorFilterRow(
                            options = state.operatorOptions,
                            selected = state.selectedOperator,
                            onSelect = onSelectOperator,
                        )
                    }
                }
                item(key = "overview") { OverviewPanels(state, horizontal) }
                if (state.staleCount > 0) {
                    item(key = "stale") {
                        MessageBanner(
                            text = "${state.staleCount} av ${state.watchedCount} hållplatser visar sparad data – " +
                                "den senaste uppdateringen misslyckades.",
                            tone = BannerTone.WARNING,
                            modifier = horizontal,
                        )
                    }
                }
                item(key = "issues-header") { SectionHeader("Avvikelser just nu", horizontal) }
                item(key = "issues") {
                    PanelCard(horizontal.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                        if (state.issues.isEmpty()) {
                            Text(
                                "Inga förseningar, tidiga eller inställda avgångar i urvalet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TrafikpulsTheme.colors.inkSecondary,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        state.issues.forEachIndexed { index, issue ->
                            if (index > 0) HorizontalDivider(color = TrafikpulsTheme.colors.panelBorder)
                            DepartureRow(
                                entry = issue.entry,
                                policy = state.policy,
                                onClick = { onOpenTrip(issue.entry) },
                                contextLabel = "${issue.stopName} · ${issue.entry.operatorLabel}",
                            )
                        }
                    }
                }
                item(key = "stops-header") {
                    SectionHeader(
                        "Bevakade hållplatser (${state.watchedCount}/${WatchlistRepository.MAX_WATCHED})",
                        horizontal,
                    ) {
                        TextButton(onClick = onOpenSearch) { Text("Lägg till") }
                    }
                }
                items(state.stops, key = { it.areaId }) { card ->
                    StopCard(
                        card = card,
                        onClick = { onOpenStop(card.areaId, card.name) },
                        onRemove = { onRemoveStop(card.areaId) },
                        modifier = horizontal,
                    )
                }
                item(key = "footer") {
                    AttributionFooter(
                        isDemo = state.isDemo,
                        freshness = state.lastUpdated?.let { "Senast uppdaterad ${Format.clock(it)} · uppdateras varje minut" },
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewPanels(state: DashboardUiState, modifier: Modifier) {
    val kpis = state.overall
    val colors = TrafikpulsTheme.colors
    val lateMinutes = state.policy.lateThresholdSeconds / 60
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Punktlighet", style = MaterialTheme.typography.titleSmall, color = colors.inkSecondary)
                    Text(
                        Format.percent(kpis.punctuality),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                HealthBadge(state.overallHealth)
            }
            Text(
                "${Format.count(kpis.onTime)} av ${Format.count(kpis.tracked)} avgångar med realtid är i tid " +
                    "(högst $lateMinutes min sen, högst 1 min tidig).",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Medelförsening ${Format.averageDelay(kpis.averageDelaySeconds)} · " +
                    "störst ${kpis.maxDelaySeconds?.let(Format::delay) ?: "–"} · " +
                    "${Format.count(kpis.total)} avgångar",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiTile(
                label = "Försenade",
                value = Format.count(kpis.late),
                supporting = "mer än $lateMinutes min",
                icon = Icons.Filled.Schedule,
                iconTint = colors.serious,
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                label = "Inställda",
                value = Format.count(kpis.canceled),
                supporting = "av ${Format.count(kpis.total)} avgångar",
                icon = Icons.Filled.Cancel,
                iconTint = colors.critical,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiTile(
                label = "Tidiga avgångar",
                value = Format.count(kpis.early),
                supporting = "mer än 1 min före",
                icon = Icons.Filled.FastForward,
                iconTint = colors.warning,
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                label = "Realtidstäckning",
                value = Format.percent(kpis.realtimeCoverage),
                supporting = "avgångar med fordonsdata",
                icon = Icons.Filled.GpsFixed,
                iconTint = colors.inkMuted,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StopCard(
    card: StopCardUi,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TrafikpulsTheme.colors
    PanelCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(start = 16.dp, top = 8.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                Text(
                    card.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    card.modes.sortedBy { it.ordinal }.forEach { mode ->
                        Icon(mode.icon(), contentDescription = mode.label, tint = colors.inkSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
            HealthBadge(card.health)
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Fler val för ${card.name}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Sluta bevaka") },
                        leadingIcon = { Icon(Icons.Outlined.StarOutline, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        }
        Column(Modifier.padding(end = 16.dp)) {
            Spacer(Modifier.height(6.dp))
            when {
                card.isLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                !card.hasData && card.error != null -> Text(
                    Format.errorMessage(card.error),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSecondary,
                )
                else -> Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    MiniStat("Punktlighet", Format.percent(card.kpis.punctuality))
                    MiniStat("Avgångar", Format.count(card.kpis.total))
                    MiniStat("Försenade", Format.count(card.kpis.late))
                    MiniStat("Inställda", Format.count(card.kpis.canceled))
                }
            }
            if (card.staleSince != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Sparad data från ${Format.clock(card.staleSince)} (${Format.errorShort(card.error)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
            }
            if (card.followedLines > 0) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FilterList, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (card.followedLines == 1) "Följer 1 linje" else "Följer ${card.followedLines} linjer",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
            }
            if (card.alertCount > 0) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Campaign, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (card.alertCount == 1) "1 trafikmeddelande" else "${card.alertCount} trafikmeddelanden",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TrafikpulsTheme.colors.inkSecondary)
    }
}
