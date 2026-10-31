package com.simon.trafikpuls.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simon.trafikpuls.domain.PunctualityPolicy
import com.simon.trafikpuls.domain.TripInsights
import com.simon.trafikpuls.domain.model.TripCall
import com.simon.trafikpuls.domain.model.TripDetails
import com.simon.trafikpuls.ui.components.AttributionFooter
import com.simon.trafikpuls.ui.components.BannerTone
import com.simon.trafikpuls.ui.components.DelayBadge
import com.simon.trafikpuls.ui.components.ErrorState
import com.simon.trafikpuls.ui.components.KpiTile
import com.simon.trafikpuls.ui.components.LineBadge
import com.simon.trafikpuls.ui.components.MessageBanner
import com.simon.trafikpuls.ui.components.PanelCard
import com.simon.trafikpuls.ui.components.SectionHeader
import com.simon.trafikpuls.ui.components.freshnessText
import com.simon.trafikpuls.ui.format.Format
import com.simon.trafikpuls.ui.theme.TrafikpulsTheme
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(
    tripId: String,
    startDate: String,
    highlightStopId: String?,
    onBack: () -> Unit,
    viewModel: TripViewModel = viewModel(factory = TripViewModel.factory(tripId, startDate, highlightStopId)),
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
                    val trip = state.trip
                    Text(
                        if (trip == null) "Tur" else "${Format.lineLabel(trip.line)} mot ${trip.line.direction}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tillbaka")
                    }
                },
            )
        },
    ) { padding ->
        val trip = state.trip
        val insights = state.insights
        val error = state.error
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                trip == null || insights == null -> LazyColumn(Modifier.fillMaxSize()) {
                    item { ErrorState(error = error ?: return@item, onRetry = viewModel::refresh) }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (error != null) {
                        item(key = "stale") {
                            MessageBanner(
                                text = "Kunde inte uppdatera: ${Format.errorShort(error)}. Visar senast hämtade data.",
                                tone = BannerTone.WARNING,
                            )
                        }
                    }
                    item(key = "header") { TripHeader(trip, insights) }
                    item(key = "insights") { InsightTiles(insights, trip) }
                    item(key = "chart") {
                        ChartPanel(
                            trip = trip,
                            insights = insights,
                            policy = state.policy,
                            highlightIndex = state.highlightIndex,
                            selectedIndex = state.selectedIndex,
                            onSelect = viewModel::select,
                        )
                    }
                    item(key = "calls-header") { SectionHeader("Hållplatser (${trip.calls.size})") }
                    item(key = "calls") {
                        CallList(
                            trip = trip,
                            policy = state.policy,
                            lastPassedIndex = insights.lastPassedIndex,
                            highlightIndex = state.highlightIndex,
                            selectedIndex = state.selectedIndex,
                            onSelect = viewModel::select,
                        )
                    }
                    item(key = "footer") { AttributionFooter(isDemo = state.isDemo, freshness = freshnessText(state.meta)) }
                }
            }
        }
    }
}

@Composable
private fun TripHeader(trip: TripDetails, insights: TripInsights) {
    PanelCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineBadge(trip.line)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(trip.line.direction, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(trip.operatorName, trip.agencyName?.takeIf { it != trip.operatorName }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = TrafikpulsTheme.colors.inkSecondary,
                )
            }
        }
        TripEndpoints(trip)
        Spacer(Modifier.height(8.dp))
        Text(
            listOfNotNull(
                trip.technicalNumber?.let { "Turnummer $it" },
                trip.startDate.toString(),
                "realtid ${Format.percent(insights.realtimeShare)}",
                insights.canceledStops.takeIf { it > 0 }?.let { "$it inställda hållplatser" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = TrafikpulsTheme.colors.inkMuted,
        )
    }
}

/** Where and when the trip starts and ends - "Start Sjöängen 06:42 (avgick 06:44)". */
@Composable
private fun TripEndpoints(trip: TripDetails) {
    val first = trip.calls.firstOrNull() ?: return
    val last = trip.calls.last()
    Spacer(Modifier.height(10.dp))
    EndpointRow("Start", first.stopName, first.scheduledTime, first.expectedDeparture, first.isRealtime, pastTense = "avgick")
    if (trip.calls.size > 1) {
        EndpointRow("Slut", last.stopName, last.scheduledTime, last.expectedArrival, last.isRealtime, pastTense = "beräknad")
    }
}

@Composable
private fun EndpointRow(
    label: String,
    stopName: String,
    scheduled: LocalDateTime?,
    expected: LocalDateTime?,
    isRealtime: Boolean,
    pastTense: String,
) {
    val text = buildString {
        append(stopName)
        scheduled?.let { append(" ${Format.time(it)}") }
        if (isRealtime && expected != null && scheduled != null && Format.time(expected) != Format.time(scheduled)) {
            append(" ($pastTense ${Format.time(expected)})")
        }
    }
    Row {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TrafikpulsTheme.colors.inkMuted, modifier = Modifier.width(40.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InsightTiles(insights: TripInsights, trip: TripDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val passedStop = insights.lastPassedIndex?.let { trip.calls[it].stopName }
            KpiTile(
                label = "Försening nu",
                value = insights.currentDelaySeconds?.let(Format::delay) ?: "–",
                supporting = passedStop?.let { "senast vid $it" } ?: "turen har inte startat",
                icon = Icons.Filled.MyLocation,
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                label = "Vid ändhållplatsen",
                value = insights.finalDelaySeconds?.let(Format::delay) ?: "–",
                supporting = if (insights.lastPassedIndex == trip.calls.lastIndex) "utfall" else "prognos",
                icon = Icons.Filled.Flag,
                modifier = Modifier.weight(1f),
            )
        }
        insights.worstSegment?.let { segment ->
            PanelCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = TrafikpulsTheme.colors.serious,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Störst förseningsökning", style = MaterialTheme.typography.labelMedium, color = TrafikpulsTheme.colors.inkSecondary)
                        Text(
                            "${segment.fromStop} → ${segment.toStop}: ${Format.delay(segment.deltaSeconds)}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartPanel(
    trip: TripDetails,
    insights: TripInsights,
    policy: PunctualityPolicy,
    highlightIndex: Int?,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
) {
    val colors = TrafikpulsTheme.colors
    PanelCard(Modifier.fillMaxWidth()) {
        Text("Försening per hållplats (min)", style = MaterialTheme.typography.titleSmall)
        val selected = selectedIndex?.let { trip.calls.getOrNull(it) }
        Text(
            if (selected == null) {
                "Tryck i diagrammet för att se en hållplats."
            } else {
                buildString {
                    append(selected.stopName)
                    selected.expectedTime?.let { append(" · ${Format.time(it)}") }
                    append(" · ")
                    append(
                        when {
                            selected.isCanceled -> "inställd"
                            !selected.isRealtime -> "ingen realtid"
                            else -> Format.delay(selected.delaySeconds ?: 0)
                        },
                    )
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkSecondary,
        )
        Spacer(Modifier.height(8.dp))
        if (insights.points.none { it.isRealtime }) {
            Text(
                "Turen saknar realtidsdata – diagrammet visas när fordonet rapporterar position.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkSecondary,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            DelayChart(
                points = insights.points,
                lateThresholdSeconds = policy.lateThresholdSeconds,
                worstSegment = insights.worstSegment,
                highlightIndex = highlightIndex,
                lastPassedIndex = insights.lastPassedIndex,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                description = chartDescription(trip, insights),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    trip.calls.first().stopName,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMuted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    trip.calls.last().stopName,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LineKey(colors.warning, "Gräns ${Format.delay(policy.lateThresholdSeconds)}")
                if (insights.worstSegment != null) LineKey(colors.serious, "Störst ökning")
                if (insights.lastPassedIndex != null && insights.lastPassedIndex < trip.calls.lastIndex) {
                    LineKey(colors.series.copy(alpha = 0.45f), "Prognos")
                }
            }
        }
    }
}

@Composable
private fun LineKey(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(14.dp).height(2.dp).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TrafikpulsTheme.colors.inkSecondary)
    }
}

private fun chartDescription(trip: TripDetails, insights: TripInsights): String = buildString {
    append("Diagram över förseningen längs ${trip.calls.size} hållplatser.")
    insights.worstSegment?.let {
        append(" Störst ökning mellan ${it.fromStop} och ${it.toStop}: ${Format.delay(it.deltaSeconds)}.")
    }
    insights.finalDelaySeconds?.let { append(" Vid ändhållplatsen: ${Format.delay(it)}.") }
}

/** The table twin of the chart: every stop with times and delay. */
@Composable
private fun CallList(
    trip: TripDetails,
    policy: PunctualityPolicy,
    lastPassedIndex: Int?,
    highlightIndex: Int?,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
) {
    PanelCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
        trip.calls.forEachIndexed { index, call ->
            if (index > 0) HorizontalDivider(color = TrafikpulsTheme.colors.panelBorder)
            CallRow(
                call = call,
                policy = policy,
                passed = lastPassedIndex != null && index <= lastPassedIndex,
                highlighted = index == highlightIndex,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun CallRow(
    call: TripCall,
    policy: PunctualityPolicy,
    passed: Boolean,
    highlighted: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = TrafikpulsTheme.colors
    val background = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (passed) colors.series else colors.panelBorder),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                call.stopName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                textDecoration = if (call.isCanceled) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val times = buildString {
                val scheduled = call.scheduledTime
                val expected = call.expectedTime
                scheduled?.let { append("Tidtabell ${Format.time(it)}") }
                if (call.isRealtime && expected != null && scheduled != null &&
                    Format.time(expected) != Format.time(scheduled)
                ) {
                    append(" · beräknad ${Format.time(expected)}")
                }
                call.platform?.let { append(" · läge $it") }
                if (highlighted) append(" · din hållplats")
            }
            Text(times, style = MaterialTheme.typography.bodySmall, color = colors.inkSecondary)
        }
        Spacer(Modifier.width(8.dp))
        DelayBadge(
            status = policy.classify(call.delaySeconds ?: 0, call.isRealtime, call.isCanceled),
            delaySeconds = call.delaySeconds ?: 0,
        )
    }
}
