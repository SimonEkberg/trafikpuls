package com.simon.trafikpuls.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simon.trafikpuls.BuildConfig
import com.simon.trafikpuls.data.settings.AppSettings
import com.simon.trafikpuls.ui.components.PanelCard
import com.simon.trafikpuls.ui.theme.TrafikpulsTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Inställningar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tillbaka")
                    }
                },
            )
        },
    ) { padding ->
        val current = settings
        if (current == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DataSourceSection(
                settings = current,
                onSaveKey = { key ->
                    viewModel.saveApiKey(key)
                    notify("API-nyckeln är sparad – appen använder nu livedata.")
                },
                onClearKey = viewModel::clearApiKey,
                onDemoMode = viewModel::setDemoMode,
            )
            PunctualitySection(current, onThreshold = viewModel::setLateThreshold)
            AboutSection()
        }
    }
}

@Composable
private fun SettingsPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    PanelCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = TrafikpulsTheme.colors.inkSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DataSourceSection(
    settings: AppSettings,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit,
    onDemoMode: (Boolean) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var draft by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    SettingsPanel("Datakälla") {
        SwitchRow(
            title = "Demoläge",
            description = "Fiktiv trafik som genereras i appen. Fungerar utan nyckel och utan nätverk.",
            checked = settings.demoMode,
            onChange = onDemoMode,
        )
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = TrafikpulsTheme.colors.panelBorder)
        Text("Trafiklab API-nyckel", style = MaterialTheme.typography.bodyLarge)
        Text(
            when {
                settings.apiKeyOverride.isNotBlank() -> "Sparad i appen: ••••${settings.apiKeyOverride.takeLast(4)}"
                settings.hasBuildApiKey -> "Använder nyckeln från bygget (local.properties)."
                else -> "Ingen nyckel inlagd."
            },
            style = MaterialTheme.typography.bodySmall,
            color = TrafikpulsTheme.colors.inkSecondary,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ny nyckel") },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (visible) "Dölj nyckel" else "Visa nyckel",
                    )
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSaveKey(draft)
                    draft = ""
                },
                enabled = draft.length >= 8,
            ) { Text("Spara nyckel") }
            if (settings.apiKeyOverride.isNotBlank()) {
                OutlinedButton(onClick = onClearKey) { Text("Ta bort") }
            }
        }
        TextButton(onClick = { uriHandler.openUri("https://developer.trafiklab.se") }) {
            Text("Skaffa nyckel på developer.trafiklab.se (Trafiklab Realtime APIs)")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PunctualitySection(settings: AppSettings, onThreshold: (Int) -> Unit) {
    val options = listOf(1, 2, 3, 5)
    SettingsPanel("Punktlighet") {
        Text(
            "En avgång räknas som i tid om den är högst ${settings.lateThresholdMinutes} min sen och högst 1 min tidig.",
            style = MaterialTheme.typography.bodySmall,
            color = TrafikpulsTheme.colors.inkSecondary,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, minutes ->
                SegmentedButton(
                    selected = settings.lateThresholdMinutes == minutes,
                    onClick = { onThreshold(minutes) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text("$minutes min") },
                )
            }
        }
    }
}

@Composable
private fun AboutSection() {
    SettingsPanel("Om Trafikpuls") {
        Text(
            "Proof of concept: mobil driftövervakning av kollektivtrafik byggd på Trafiklabs öppna API:er. " +
                "Inte en officiell produkt.",
            style = MaterialTheme.typography.bodySmall,
            color = TrafikpulsTheme.colors.inkSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Data: Trafiklab.se (Trafiklab Realtime APIs), licens CC BY 4.0.",
            style = MaterialTheme.typography.bodySmall,
            color = TrafikpulsTheme.colors.inkSecondary,
        )
        Text(
            "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = TrafikpulsTheme.colors.inkMuted,
        )
    }
}
