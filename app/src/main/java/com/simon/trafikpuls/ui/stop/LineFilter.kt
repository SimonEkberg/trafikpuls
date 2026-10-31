package com.simon.trafikpuls.ui.stop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.simon.trafikpuls.domain.LineOption
import com.simon.trafikpuls.ui.components.LineBadge
import com.simon.trafikpuls.ui.theme.TrafikpulsTheme

/** "Linjer: alla" / "Linjer: 2 valda" - opens the line picker. */
@Composable
fun LineFilterChip(
    followed: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = followed > 0,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        label = { Text(if (followed == 0) "Linjer: alla" else "Linjer: $followed valda") },
        leadingIcon = { Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
    )
}

/**
 * Pick the lines (per direction) to follow at this stop. Nothing selected = all lines.
 * The choice is saved for the stop and also applies on the overview and the disruptions tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineFilterSheet(
    options: List<LineOption>,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by rememberSaveable { mutableStateOf(options.filter { it.isSelected }.map { it.key }.toSet()) }
    val ordered = remember(options) { options }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                "Vilka linjer vill du följa?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                "Inget valt = alla linjer. Valet sparas för hållplatsen och gäller även i översikten.",
                style = MaterialTheme.typography.bodySmall,
                color = TrafikpulsTheme.colors.inkSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (ordered.isEmpty()) {
                Text(
                    "Inga avgångar att välja bland just nu.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(20.dp),
                )
            }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(ordered, key = { it.key }) { option ->
                    val checked = option.key in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = if (checked) selected - option.key else selected + option.key }
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        LineBadge(option.line)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("mot ${option.line.direction}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when (option.departures) {
                                    0 -> "inga avgångar just nu"
                                    1 -> "1 avgång"
                                    else -> "${option.departures} avgångar"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TrafikpulsTheme.colors.inkSecondary,
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = { onSave(emptySet()) }) { Text("Visa alla") }
                Button(onClick = { onSave(selected) }) { Text("Spara") }
            }
        }
    }
}
