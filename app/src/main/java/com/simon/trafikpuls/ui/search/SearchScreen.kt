package com.simon.trafikpuls.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simon.trafikpuls.domain.model.StopGroup
import com.simon.trafikpuls.ui.components.EmptyState
import com.simon.trafikpuls.ui.components.ErrorState
import com.simon.trafikpuls.ui.components.PanelCard
import com.simon.trafikpuls.ui.components.icon
import com.simon.trafikpuls.ui.format.Format
import com.simon.trafikpuls.ui.theme.TrafikpulsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenStop: (areaId: String, name: String) -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("Sök hållplats") }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("T.ex. Kista centrum eller Malmö C") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Rensa")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )
            when (val result = state.result) {
                SearchResult.Idle -> EmptyState(
                    icon = Icons.Outlined.TravelExplore,
                    title = "Hitta hållplatser att bevaka",
                    message = if (state.isDemo) {
                        "Demoläge: sök bland demohållplatserna, t.ex. \"Malmö\", \"Stockholm\", \"Göteborg\" eller \"Kista\"."
                    } else {
                        "Sök bland alla Sveriges hållplatser via Trafiklab. Stjärnmärk de du vill följa i översikten."
                    },
                )
                SearchResult.TooShort -> Hint("Skriv minst ${SearchViewModel.MIN_QUERY_LENGTH} tecken.")
                SearchResult.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                SearchResult.NoMatches -> EmptyState(
                    icon = Icons.Filled.SearchOff,
                    title = "Inga träffar",
                    message = "Inga hållplatser matchar \"${state.query.trim()}\".",
                )
                is SearchResult.Failed -> ErrorState(error = result.error, onRetry = viewModel::retry)
                is SearchResult.Results -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    item {
                        PanelCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                            result.groups.forEachIndexed { index, group ->
                                if (index > 0) HorizontalDivider(color = TrafikpulsTheme.colors.panelBorder)
                                StopGroupRow(
                                    group = group,
                                    isWatched = group.id in state.watchedIds,
                                    onToggleWatch = { viewModel.toggleWatch(group) },
                                    onClick = { onOpenStop(group.id, group.name) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = TrafikpulsTheme.colors.inkSecondary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

@Composable
private fun StopGroupRow(
    group: StopGroup,
    isWatched: Boolean,
    onToggleWatch: () -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = TrafikpulsTheme.colors.panel),
        headlineContent = { Text(group.name) },
        supportingContent = {
            Column {
                Text(
                    listOfNotNull(group.areaType.label, Format.departuresPerDay(group.averageDailyDepartures)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = TrafikpulsTheme.colors.inkSecondary,
                )
                if (group.transportModes.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                        group.transportModes.sortedBy { it.ordinal }.forEach { mode ->
                            Icon(mode.icon(), contentDescription = mode.label, tint = TrafikpulsTheme.colors.inkSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onToggleWatch) {
                if (isWatched) {
                    Icon(Icons.Filled.Star, contentDescription = "Sluta bevaka ${group.name}", tint = TrafikpulsTheme.colors.warning)
                } else {
                    Icon(Icons.Outlined.StarOutline, contentDescription = "Bevaka ${group.name}")
                }
            }
        },
    )
}
