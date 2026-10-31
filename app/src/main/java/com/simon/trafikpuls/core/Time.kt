package com.simon.trafikpuls.core

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Trafiklab returns Swedish local times without offset, so every conversion goes through this zone. */
val SwedishZone: ZoneId = ZoneId.of("Europe/Stockholm")

object TimeFormats {
    val hourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val hourMinuteSecond: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    /** The API's time path parameter: minutes precision, seconds are not supported. */
    val apiMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    /** The API's timestamp format in responses. */
    val apiSecond: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    val compactDate: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
}

fun Clock.nowInSweden(): LocalDateTime = LocalDateTime.now(withZone(SwedishZone))

/** Parses an API timestamp; tolerates an explicit offset in case the API ever starts sending one. */
fun parseApiDateTime(raw: String?): LocalDateTime? {
    if (raw.isNullOrBlank()) return null
    return runCatching { LocalDateTime.parse(raw.trim()) }.getOrNull()
        ?: runCatching {
            OffsetDateTime.parse(raw.trim()).atZoneSameInstant(SwedishZone).toLocalDateTime()
        }.getOrNull()
}

/** Accepts both yyyy-MM-dd and yyyyMMdd, as the API documents both. */
fun parseApiDate(raw: String?): LocalDate? {
    if (raw.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(raw.trim()) }.getOrNull()
        ?: runCatching { LocalDate.parse(raw.trim(), TimeFormats.compactDate) }.getOrNull()
}
