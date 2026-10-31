package com.simon.trafikpuls.data.demo

import com.simon.trafikpuls.data.remote.dto.AlertDto
import com.simon.trafikpuls.domain.model.TransportMode
import com.simon.trafikpuls.domain.model.WatchedStop

// Fictional but plausible network for demo mode. Stop names are real places; lines,
// operators and disruptions are made up and only used when demo mode is on.

internal enum class Reliability { STEADY, NORMAL, TROUBLED }

internal data class DemoLine(
    val key: String,
    val designation: String,
    val name: String? = null,
    val mode: TransportMode,
    val direction: String,
    val destination: String = direction,
    val agency: String,
    val operator: String,
    val headwayMinutes: Int,
    val offsetMinutes: Int,
    val reliability: Reliability,
    val platform: String?,
    val stopPool: List<String>,
    val routeLength: Int = 14,
    val minutesBetweenStops: Int = 2,
    val alert: AlertDto? = null,
)

internal data class DemoStop(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val averageDaily: Double,
    val lines: List<DemoLine>,
    val stopAlerts: List<AlertDto> = emptyList(),
) {
    val transportModes: Set<TransportMode> get() = lines.map { it.mode }.toSet()
}

object DemoNetwork {

    private const val NOBINA = "Nobina Sverige AB"
    private const val KEOLIS = "Keolis Sverige AB"
    private const val TRANSDEV = "Transdev Sverige AB"
    private const val MTR = "MTR Tunnelbanan AB"

    private val malmoPool = listOf(
        "Triangeln", "Södervärn", "Möllevångstorget", "Dalaplan", "Värnhemstorget", "Gustav Adolfs torg",
        "Fridhemstorget", "Rosengård centrum", "Hyllie station", "Västra hamnen", "Kockums fritid", "Mobilia",
        "Lindängen centrum", "Limhamns torg", "Kroksbäck", "Lorensborg", "Universitetsholmen", "Nobeltorget",
        "Annelund", "Jägersro",
    )
    private val stockholmPool = listOf(
        "Sergels torg", "Kungsträdgården", "Odenplan", "Fridhemsplan", "Sankt Eriksplan", "Slussen",
        "Medborgarplatsen", "Hornstull", "Karlaplan", "Stureplan", "Östermalmstorg", "Rådmansgatan", "Hötorget",
        "Norra Bantorget", "Kungsholmstorg", "Rådhuset", "Nybroplan", "Djurgårdsbron", "Tekniska högskolan", "Gärdet",
    )
    private val goteborgPool = listOf(
        "Brunnsparken", "Nordstan", "Kungsportsplatsen", "Valand", "Korsvägen", "Chalmers", "Järntorget",
        "Domkyrkan", "Hjalmar Brantingsplatsen", "Frölunda torg", "Svingeln", "Olskrokstorget", "Redbergsplatsen",
        "Munkebäckstorget", "Marklandsgatan", "Linnéplatsen", "Vasaplatsen", "Gamlestads torg", "Lindholmen",
    )
    private val kistaPool = listOf(
        "Kista gård", "Kista Galleria", "Husby centrum", "Akalla", "Helenelund", "Sollentuna centrum", "Rinkeby",
        "Tensta centrum", "Spånga station", "Sundbyberg centrum", "Bromma flygplats", "Ulvsunda", "Hallonbergen",
        "Rissne", "Edsberg", "Ärvinge",
    )
    private val railPool = listOf(
        "Lund C", "Hässleholm C", "Alvesta", "Nässjö C", "Linköping C", "Norrköping C", "Katrineholm C",
        "Södertälje Syd", "Hyllie", "Kastrup lufthavn", "Helsingborg C", "Landskrona", "Herrljunga", "Skövde C",
        "Hallsberg", "Alingsås", "Trollhättan C",
    )

    private val kistaRoadworks = AlertDto(
        type = "CONSTRUCTION",
        title = "Vägarbete på Kistavägen",
        text = "Linje 178 och 179 körs om via Kista allé. Räkna med upp till 10 minuters förseningar.",
    )

    private val malmo = DemoStop(
        id = "740000003", name = "Malmö Centralstation", lat = 55.608777, lon = 13.000216, averageDaily = 2410.5,
        stopAlerts = listOf(
            AlertDto(
                type = "CONSTRUCTION",
                title = "Hållplatsläge C tillfälligt flyttat",
                text = "Läge C är flyttat cirka 50 meter västerut under ombyggnaden av stationsområdet.",
            ),
        ),
        lines = listOf(
            DemoLine("M2", "2", mode = TransportMode.BUS, direction = "Lindängen", agency = "Skånetrafiken",
                operator = NOBINA, headwayMinutes = 7, offsetMinutes = 1, reliability = Reliability.NORMAL,
                platform = "A", stopPool = malmoPool, routeLength = 16),
            DemoLine("M3", "3", name = "Ringlinjen", mode = TransportMode.BUS,
                direction = "Ringlinjen via Fridhemstorget", destination = "Värnhemstorget", agency = "Skånetrafiken",
                operator = NOBINA, headwayMinutes = 6, offsetMinutes = 3, reliability = Reliability.NORMAL,
                platform = "D", stopPool = malmoPool, routeLength = 18),
            DemoLine("M4", "4", mode = TransportMode.BUS, direction = "Bunkeflostrand", agency = "Skånetrafiken",
                operator = NOBINA, headwayMinutes = 10, offsetMinutes = 4, reliability = Reliability.STEADY,
                platform = "B", stopPool = malmoPool),
            DemoLine("M5", "5", mode = TransportMode.BUS, direction = "Stenkällan", agency = "Skånetrafiken",
                operator = NOBINA, headwayMinutes = 8, offsetMinutes = 2, reliability = Reliability.TROUBLED,
                platform = "C", stopPool = malmoPool, routeLength = 15,
                alert = AlertDto(
                    type = "CONSTRUCTION",
                    title = "Linje 5 omleds vid Södervärn",
                    text = "Linje 5 körs om via Nobeltorget på grund av ledningsarbete. Räkna med 5–10 minuters extra restid.",
                )),
            DemoLine("M7", "7", mode = TransportMode.BUS, direction = "Svågertorp", agency = "Skånetrafiken",
                operator = NOBINA, headwayMinutes = 12, offsetMinutes = 6, reliability = Reliability.NORMAL,
                platform = "E", stopPool = malmoPool),
            DemoLine("M150", "150", mode = TransportMode.BUS, direction = "Klågerup", agency = "Skånetrafiken",
                operator = "Bergkvarabuss AB", headwayMinutes = 30, offsetMinutes = 11,
                reliability = Reliability.NORMAL, platform = "F", stopPool = malmoPool, minutesBetweenStops = 3),
            DemoLine("MOT", "1111", name = "Öresundståg", mode = TransportMode.TRAIN, direction = "København H",
                agency = "Öresundståg", operator = TRANSDEV, headwayMinutes = 20, offsetMinutes = 8,
                reliability = Reliability.NORMAL, platform = "4a", stopPool = railPool, routeLength = 9,
                minutesBetweenStops = 9),
            DemoLine("MSJ", "541", name = "SJ Snabbtåg", mode = TransportMode.TRAIN, direction = "Stockholm C",
                agency = "SJ", operator = "SJ AB", headwayMinutes = 60, offsetMinutes = 22,
                reliability = Reliability.NORMAL, platform = "2", stopPool = railPool, routeLength = 9,
                minutesBetweenStops = 25),
        ),
    )

    private val stockholm = DemoStop(
        id = "740000001", name = "Stockholm Centralstation", lat = 59.331537, lon = 18.054943, averageDaily = 3198.9,
        stopAlerts = listOf(
            AlertDto(
                type = "TECHNICAL_PROBLEM",
                title = "Hiss ur funktion",
                text = "Hissen mellan Klarabergsviadukten och spårområdet är ur funktion. Använd hissen vid norra uppgången.",
            ),
        ),
        lines = listOf(
            DemoLine("S1", "1", mode = TransportMode.BUS, direction = "Stora Essingen", agency = "SL",
                operator = KEOLIS, headwayMinutes = 6, offsetMinutes = 2, reliability = Reliability.NORMAL,
                platform = "H", stopPool = stockholmPool),
            DemoLine("S3", "3", mode = TransportMode.BUS, direction = "Karolinska sjukhuset", agency = "SL",
                operator = KEOLIS, headwayMinutes = 7, offsetMinutes = 4, reliability = Reliability.NORMAL,
                platform = "G", stopPool = stockholmPool),
            DemoLine("S53", "53", mode = TransportMode.BUS, direction = "Henriksdalsberget", agency = "SL",
                operator = KEOLIS, headwayMinutes = 10, offsetMinutes = 5, reliability = Reliability.TROUBLED,
                platform = "F", stopPool = stockholmPool,
                alert = AlertDto(
                    type = "OTHER_CAUSE",
                    title = "Framkomlighetsproblem vid Slussen",
                    text = "Kraftig trafikbelastning vid Slussen ger förseningar i båda riktningarna.",
                )),
            DemoLine("S65", "65", mode = TransportMode.BUS, direction = "Skeppsholmen", agency = "SL",
                operator = NOBINA, headwayMinutes = 12, offsetMinutes = 7, reliability = Reliability.NORMAL,
                platform = "E", stopPool = stockholmPool),
            DemoLine("S69", "69", mode = TransportMode.BUS, direction = "Kaknästornet", agency = "SL",
                operator = NOBINA, headwayMinutes = 10, offsetMinutes = 1, reliability = Reliability.STEADY,
                platform = "D", stopPool = stockholmPool),
            DemoLine("S13", "13", mode = TransportMode.METRO, direction = "Ropsten", agency = "SL",
                operator = MTR, headwayMinutes = 5, offsetMinutes = 0, reliability = Reliability.STEADY,
                platform = "2", stopPool = stockholmPool),
            DemoLine("S14", "14", mode = TransportMode.METRO, direction = "Mörby centrum", agency = "SL",
                operator = MTR, headwayMinutes = 5, offsetMinutes = 2, reliability = Reliability.STEADY,
                platform = "1", stopPool = stockholmPool),
            DemoLine("S17", "17", mode = TransportMode.METRO, direction = "Åkeshov", agency = "SL",
                operator = MTR, headwayMinutes = 10, offsetMinutes = 3, reliability = Reliability.NORMAL,
                platform = "3", stopPool = stockholmPool),
            DemoLine("SSJ", "422", name = "SJ Snabbtåg", mode = TransportMode.TRAIN, direction = "Göteborg C",
                agency = "SJ", operator = "SJ AB", headwayMinutes = 60, offsetMinutes = 35,
                reliability = Reliability.NORMAL, platform = "14", stopPool = railPool, routeLength = 8,
                minutesBetweenStops = 25),
        ),
    )

    private val goteborg = DemoStop(
        id = "740000002", name = "Göteborg Centralstation", lat = 57.709299, lon = 11.973659, averageDaily = 2875.2,
        lines = listOf(
            DemoLine("G1", "1", mode = TransportMode.TRAM, direction = "Östra sjukhuset", agency = "Västtrafik",
                operator = "Göteborgs Spårvägar AB", headwayMinutes = 10, offsetMinutes = 2,
                reliability = Reliability.STEADY, platform = "B", stopPool = goteborgPool),
            DemoLine("G3", "3", mode = TransportMode.TRAM, direction = "Kålltorp", agency = "Västtrafik",
                operator = "Göteborgs Spårvägar AB", headwayMinutes = 10, offsetMinutes = 5,
                reliability = Reliability.NORMAL, platform = "C", stopPool = goteborgPool,
                alert = AlertDto(
                    type = "MAINTENANCE",
                    title = "Spårarbete vid Linnéplatsen",
                    text = "Spårvagnarna kör med sänkt hastighet förbi Linnéplatsen. Förseningar kan förekomma.",
                )),
            DemoLine("G6", "6", mode = TransportMode.TRAM, direction = "Länsmansgården", agency = "Västtrafik",
                operator = "Göteborgs Spårvägar AB", headwayMinutes = 10, offsetMinutes = 7,
                reliability = Reliability.STEADY, platform = "D", stopPool = goteborgPool),
            DemoLine("G16", "16", mode = TransportMode.BUS, direction = "Eketrägatan", agency = "Västtrafik",
                operator = TRANSDEV, headwayMinutes = 8, offsetMinutes = 3, reliability = Reliability.NORMAL,
                platform = "F", stopPool = goteborgPool),
            DemoLine("G18", "18", mode = TransportMode.BUS, direction = "Skälltorpsvägen", agency = "Västtrafik",
                operator = NOBINA, headwayMinutes = 10, offsetMinutes = 6, reliability = Reliability.NORMAL,
                platform = "G", stopPool = goteborgPool),
            DemoLine("G19", "19", mode = TransportMode.BUS, direction = "Fredriksdal", agency = "Västtrafik",
                operator = NOBINA, headwayMinutes = 12, offsetMinutes = 9, reliability = Reliability.STEADY,
                platform = "H", stopPool = goteborgPool),
            DemoLine("GVT", "3258", name = "Västtågen", mode = TransportMode.TRAIN, direction = "Vänersborg",
                agency = "Västtrafik", operator = "SJ Götalandståg", headwayMinutes = 30, offsetMinutes = 15,
                reliability = Reliability.NORMAL, platform = "9", stopPool = railPool, routeLength = 8,
                minutesBetweenStops = 8),
        ),
    )

    private val kista = DemoStop(
        id = "990000001", name = "Kista centrum", lat = 59.403108, lon = 17.944554, averageDaily = 1320.4,
        lines = listOf(
            DemoLine("K172", "172", mode = TransportMode.BUS, direction = "Skarpnäck", agency = "SL",
                operator = NOBINA, headwayMinutes = 10, offsetMinutes = 1, reliability = Reliability.NORMAL,
                platform = "A", stopPool = kistaPool),
            DemoLine("K178", "178", mode = TransportMode.BUS, direction = "Mörby station", agency = "SL",
                operator = NOBINA, headwayMinutes = 12, offsetMinutes = 4, reliability = Reliability.TROUBLED,
                platform = "B", stopPool = kistaPool, alert = kistaRoadworks),
            DemoLine("K179", "179", mode = TransportMode.BUS, direction = "Vällingby", agency = "SL",
                operator = NOBINA, headwayMinutes = 10, offsetMinutes = 7, reliability = Reliability.TROUBLED,
                platform = "C", stopPool = kistaPool, alert = kistaRoadworks),
            DemoLine("K514", "514", mode = TransportMode.BUS, direction = "Sollentuna station", agency = "SL",
                operator = TRANSDEV, headwayMinutes = 15, offsetMinutes = 9, reliability = Reliability.STEADY,
                platform = "D", stopPool = kistaPool),
            DemoLine("K11", "11", mode = TransportMode.METRO, direction = "Kungsträdgården", agency = "SL",
                operator = MTR, headwayMinutes = 10, offsetMinutes = 3, reliability = Reliability.STEADY,
                platform = "1", stopPool = kistaPool),
        ),
    )

    internal val stops: List<DemoStop> = listOf(malmo, stockholm, goteborg, kista)

    /** Seeded into the demo watchlist on first launch. */
    val defaultWatchlist: List<WatchedStop>
        get() = stops.map { WatchedStop(it.id, it.name, it.transportModes) }

    /** Unknown ids get a generic stop so demo mode never shows an error for a valid-looking id. */
    internal fun stop(areaId: String): DemoStop = stops.firstOrNull { it.id == areaId } ?: DemoStop(
        id = areaId,
        name = "Demohållplats $areaId",
        lat = 59.0,
        lon = 15.0,
        averageDaily = 300.0,
        lines = listOf(
            DemoLine("X1", "1", mode = TransportMode.BUS, direction = "Centrum", agency = "Demotrafik",
                operator = NOBINA, headwayMinutes = 10, offsetMinutes = 2, reliability = Reliability.NORMAL,
                platform = "A", stopPool = kistaPool),
            DemoLine("X2", "2", mode = TransportMode.BUS, direction = "Resecentrum", agency = "Demotrafik",
                operator = TRANSDEV, headwayMinutes = 15, offsetMinutes = 6, reliability = Reliability.STEADY,
                platform = "B", stopPool = kistaPool),
        ),
    )
}
