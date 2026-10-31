package com.simon.trafikpuls.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.simon.trafikpuls.domain.model.TransportMode

// Neutral surfaces and ink follow the data-viz reference palette; the brand accent is a step
// from its blue ramp. Status and transport-mode colors are validated for colorblind safety
// in both themes and are always paired with an icon or a text label.

@Immutable
data class TrafikpulsColors(
    val panel: Color,
    val panelBorder: Color,
    val gridline: Color,
    val baseline: Color,
    val inkSecondary: Color,
    val inkMuted: Color,
    val series: Color,
    val good: Color,
    val warning: Color,
    val serious: Color,
    val critical: Color,
    val bus: Color,
    val train: Color,
    val metro: Color,
    val tram: Color,
    val boat: Color,
    val taxi: Color,
) {
    fun forMode(mode: TransportMode): Color = when (mode) {
        TransportMode.BUS -> bus
        TransportMode.TRAIN -> train
        TransportMode.METRO -> metro
        TransportMode.TRAM -> tram
        TransportMode.BOAT -> boat
        TransportMode.TAXI -> taxi
        TransportMode.UNKNOWN -> inkMuted
    }
}

// Status colors are fixed across themes.
private val StatusGood = Color(0xFF0CA30C)
private val StatusWarning = Color(0xFFFAB219)
private val StatusSerious = Color(0xFFEC835A)
private val StatusCritical = Color(0xFFD03B3B)

private val LightTrafikpulsColors = TrafikpulsColors(
    panel = Color(0xFFFCFCFB),
    panelBorder = Color(0xFFE1E0D9),
    gridline = Color(0xFFE1E0D9),
    baseline = Color(0xFFC3C2B7),
    inkSecondary = Color(0xFF52514E),
    inkMuted = Color(0xFF898781),
    series = Color(0xFF2A78D6),
    good = StatusGood,
    warning = StatusWarning,
    serious = StatusSerious,
    critical = StatusCritical,
    bus = Color(0xFF2A78D6),
    train = Color(0xFFEB6834),
    metro = Color(0xFF1BAF7A),
    tram = Color(0xFFEDA100),
    boat = Color(0xFFE87BA4),
    taxi = Color(0xFF008300),
)

private val DarkTrafikpulsColors = TrafikpulsColors(
    panel = Color(0xFF1A1A19),
    panelBorder = Color(0xFF2C2C2A),
    gridline = Color(0xFF2C2C2A),
    baseline = Color(0xFF383835),
    inkSecondary = Color(0xFFC3C2B7),
    inkMuted = Color(0xFF898781),
    series = Color(0xFF3987E5),
    good = StatusGood,
    warning = StatusWarning,
    serious = StatusSerious,
    critical = StatusCritical,
    bus = Color(0xFF3987E5),
    train = Color(0xFFD95926),
    metro = Color(0xFF199E70),
    tram = Color(0xFFC98500),
    boat = Color(0xFFD55181),
    taxi = Color(0xFF008300),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1C5CAB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE2FB),
    onPrimaryContainer = Color(0xFF0D366B),
    secondary = Color(0xFF52514E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE2FB),
    onSecondaryContainer = Color(0xFF0D366B),
    background = Color(0xFFF9F9F7),
    onBackground = Color(0xFF0B0B0B),
    surface = Color(0xFFF9F9F7),
    onSurface = Color(0xFF0B0B0B),
    surfaceVariant = Color(0xFFE8E8E3),
    onSurfaceVariant = Color(0xFF52514E),
    surfaceContainerLowest = Color(0xFFFCFCFB),
    surfaceContainerLow = Color(0xFFF4F4F1),
    surfaceContainer = Color(0xFFEFEFEB),
    surfaceContainerHigh = Color(0xFFE8E8E3),
    surfaceContainerHighest = Color(0xFFE1E0D9),
    outline = Color(0xFF898781),
    outlineVariant = Color(0xFFE1E0D9),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFBE3E3),
    onErrorContainer = Color(0xFF5C1010),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF86B6EF),
    onPrimary = Color(0xFF0D366B),
    primaryContainer = Color(0xFF184F95),
    onPrimaryContainer = Color(0xFFCDE2FB),
    secondary = Color(0xFFC3C2B7),
    onSecondary = Color(0xFF1A1A19),
    secondaryContainer = Color(0xFF184F95),
    onSecondaryContainer = Color(0xFFCDE2FB),
    background = Color(0xFF0D0D0D),
    onBackground = Color(0xFFF0EFEC),
    surface = Color(0xFF0D0D0D),
    onSurface = Color(0xFFF0EFEC),
    surfaceVariant = Color(0xFF2C2C2A),
    onSurfaceVariant = Color(0xFFC3C2B7),
    surfaceContainerLowest = Color(0xFF121211),
    surfaceContainerLow = Color(0xFF161615),
    surfaceContainer = Color(0xFF1E1E1D),
    surfaceContainerHigh = Color(0xFF262625),
    surfaceContainerHighest = Color(0xFF2F2F2D),
    outline = Color(0xFF898781),
    outlineVariant = Color(0xFF2C2C2A),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF5C1010),
    onErrorContainer = Color(0xFFFBE3E3),
)

private val LocalTrafikpulsColors = staticCompositionLocalOf { LightTrafikpulsColors }

object TrafikpulsTheme {
    val colors: TrafikpulsColors
        @Composable get() = LocalTrafikpulsColors.current
}

@Composable
fun TrafikpulsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalTrafikpulsColors provides if (darkTheme) DarkTrafikpulsColors else LightTrafikpulsColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}
