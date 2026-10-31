package com.simon.trafikpuls.ui.format

import com.simon.trafikpuls.core.AppError
import com.simon.trafikpuls.core.SwedishZone
import com.simon.trafikpuls.core.TimeFormats
import com.simon.trafikpuls.domain.DepartureStatus
import com.simon.trafikpuls.domain.model.Line
import com.simon.trafikpuls.domain.model.TimetableEntry
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Swedish presentation of times, delays, numbers and errors. */
object Format {

    private val swedish: Locale = Locale.forLanguageTag("sv-SE")
    private const val NBSP = ' '
    private const val MINUS = '−'

    fun time(time: LocalDateTime): String = time.format(TimeFormats.hourMinute)

    fun clock(instant: Instant): String = instant.atZone(SwedishZone).format(TimeFormats.hourMinuteSecond)

    /** "+4 min", "−2 min", "±0 min" - rounded to whole minutes. */
    fun delay(seconds: Int): String {
        val minutes = (abs(seconds) / 60.0).roundToInt()
        return when {
            minutes == 0 -> "±0${NBSP}min"
            seconds > 0 -> "+$minutes${NBSP}min"
            else -> "$MINUS$minutes${NBSP}min"
        }
    }

    /** Average delays get one decimal: "+1,4 min". */
    fun averageDelay(seconds: Double?): String {
        if (seconds == null) return "–"
        val minutes = seconds / 60.0
        val formatted = String.format(swedish, "%.1f", abs(minutes))
        return when {
            abs(minutes) < 0.05 -> "±0${NBSP}min"
            minutes > 0 -> "+$formatted${NBSP}min"
            else -> "$MINUS$formatted${NBSP}min"
        }
    }

    fun percent(value: Double?): String = value?.let { "${(it * 100).roundToInt()}$NBSP%" } ?: "–"

    fun count(value: Int): String = NumberFormat.getIntegerInstance(swedish).format(value)

    fun departuresPerDay(value: Double?): String? =
        value?.let { "≈$NBSP${count(it.roundToInt())} avgångar/dygn" }

    fun statusLabel(status: DepartureStatus, delaySeconds: Int): String = when (status) {
        DepartureStatus.CANCELED -> "Inställd"
        DepartureStatus.NO_REALTIME -> "Tidtabell"
        DepartureStatus.ON_TIME -> "I tid"
        DepartureStatus.EARLY, DepartureStatus.LATE, DepartureStatus.SEVERELY_LATE -> delay(delaySeconds)
    }

    fun lineLabel(line: Line): String = "${line.mode.label} ${line.designation}"

    /**
     * Where the trip starts: "Från Sjöängen", or "Startar här" when it starts at this stop.
     * Names can differ slightly between a stop and its area ("Tappström" / "Ekerö Tappström").
     */
    fun origin(entry: TimetableEntry): String? {
        val origin = entry.line.originName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val stop = entry.stopName.trim()
        val startsHere = stop.isNotEmpty() &&
            (origin.contains(stop, ignoreCase = true) || stop.contains(origin, ignoreCase = true))
        return if (startsHere) "Startar här" else "Från $origin"
    }

    fun age(fetchedAt: Instant, now: Instant): String {
        val seconds = Duration.between(fetchedAt, now).seconds
        return when {
            seconds < 45 -> "nyss"
            seconds < 3600 -> "för ${(seconds / 60).coerceAtLeast(1)} min sedan"
            else -> "kl. ${clock(fetchedAt)}"
        }
    }

    fun errorMessage(error: AppError): String = when (error) {
        AppError.MissingApiKey ->
            "Ingen API-nyckel är inlagd. Lägg in en nyckel för Trafiklab Realtime APIs eller använd demoläget."
        AppError.InvalidApiKey ->
            "Trafiklab godkände inte API-nyckeln. Kontrollera nyckeln under Inställningar."
        is AppError.RateLimited ->
            if (error.localThrottle) {
                "Appen pausar anropen en stund för att hålla sig inom Trafiklabs kvot."
            } else {
                "Trafiklabs anropsgräns är nådd" + (error.retryAfterSeconds?.let { " – försök igen om $it s." } ?: ". Försök igen om en stund.")
            }
        AppError.NotFound -> "Trafiklab hittade inte hållplatsen eller turen."
        is AppError.BadRequest -> "Trafiklab kunde inte tolka förfrågan (HTTP ${error.code})."
        is AppError.Server -> "Trafiklab har tekniska problem just nu (HTTP ${error.code})."
        is AppError.Network ->
            if (error.timeout) "Trafiklab svarade inte i tid. Kontrollera anslutningen." else "Ingen anslutning till Trafiklab."
        is AppError.Parse -> "Svaret från Trafiklab hade ett oväntat format."
        is AppError.Unexpected -> "Något gick fel" + (error.detail?.let { ": $it" } ?: ".")
    }

    /** Short form for banners next to stale data. */
    fun errorShort(error: AppError?): String = when (error) {
        null -> ""
        AppError.MissingApiKey -> "API-nyckel saknas"
        AppError.InvalidApiKey -> "ogiltig API-nyckel"
        is AppError.RateLimited -> "anropsgräns nådd"
        AppError.NotFound -> "hittades inte"
        is AppError.BadRequest -> "felaktig förfrågan"
        is AppError.Server -> "Trafiklab har driftstörning"
        is AppError.Network -> "ingen anslutning"
        is AppError.Parse -> "oväntat svar"
        is AppError.Unexpected -> "okänt fel"
    }
}
