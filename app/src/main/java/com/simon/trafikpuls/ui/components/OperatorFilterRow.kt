package com.simon.trafikpuls.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simon.trafikpuls.domain.OperatorOption

/**
 * One row of operator chips above everything it filters. Nobina is pinned first when present.
 * A saved filter that matches nothing in the current data still shows, so it can be cleared.
 */
@Composable
fun OperatorFilterRow(
    options: List<OperatorOption>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unmatchedSelection = selected?.takeIf { filter -> options.none { it.name.contains(filter, ignoreCase = true) } }
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("Alla operatörer") },
                leadingIcon = checkIcon(selected == null),
            )
        }
        if (unmatchedSelection != null) {
            item(key = "saved") {
                FilterChip(
                    selected = true,
                    onClick = { onSelect(null) },
                    label = { Text("$unmatchedSelection (0)") },
                    leadingIcon = checkIcon(true),
                )
            }
        }
        items(options, key = { it.name }) { option ->
            val isSelected = selected != null && option.name.contains(selected, ignoreCase = true)
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else option.name) },
                label = { Text("${option.name} (${option.departures})") },
                leadingIcon = checkIcon(isSelected),
            )
        }
    }
}

private fun checkIcon(visible: Boolean): (@Composable () -> Unit)? =
    if (visible) {
        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
    } else {
        null
    }
