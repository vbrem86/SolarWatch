package ch.solarwatch.data

/**
 * Momentaufnahme aus dem Solar Manager Stream. Alle Leistungen in Watt.
 */
data class SolarData(
    val pvW: Double,
    val consumptionW: Double,
    /** + = Bezug aus dem Netz, - = Einspeisung */
    val gridW: Double,
    /** + = Batterie lädt, - = Batterie entlädt; null wenn keine Batterie */
    val batteryW: Double?,
    /** Ladezustand in %; null wenn keine Batterie */
    val soc: Double?,
    /** Wärmepumpe; null wenn keine im Stream gefunden */
    val heatPump: HeatPumpData? = null,
    /** Zeitstempel laut API (ISO-String) */
    val timestamp: String?,
    val fetchedAtMillis: Long = System.currentTimeMillis(),
)

data class HeatPumpData(
    /** Geräte-ID, für Verlaufsabfragen */
    val id: String?,
    /** "temperature" der Wärmepumpe (bei Stiebel ISG: Warmwasser) in °C */
    val waterTempC: Double?,
    val outsideTempC: Double?,
    /** Roher Betriebszustand laut API ("operationState") */
    val operationState: Int?,
)

/**
 * Bedeutung von operationState. Von Solar Manager nicht dokumentiert –
 * Zuordnung anhand der eigenen Anlage ergänzen.
 */
val HEAT_PUMP_STATES: Map<Int, String> = mapOf(
    1 to "Standby", // bestätigt 14.11.2026
    2 to "Heizen", // bestätigt 14.11.2026
    3 to "Warmwasser", // bestätigt 14.11.2026
)

fun HeatPumpData.stateLabel(): String =
    operationState?.let { HEAT_PUMP_STATES[it] ?: "Zustand $it" } ?: "–"

fun formatTemp(c: Double?): String =
    c?.let { String.format(java.util.Locale.US, "%.1f °C", it) } ?: "–"

/** Zeitreihe: Zeitpunkt (Epoch-ms) -> Wert */
data class Series(val points: List<Pair<Long, Double>>)

data class History(
    val fromMillis: Long,
    val toMillis: Long,
    val pvW: Series,
    val consumptionW: Series,
    /** null wenn keine Wärmepumpe */
    val waterTempC: Series?,
    /** PV-Produktion seit Mitternacht (lokale Zeit) in Wh */
    val todayPvWh: Double,
    val fetchedAtMillis: Long = System.currentTimeMillis(),
)

/** Formatiert Watt kompakt: 850 W, 2.4 kW */
fun formatPower(w: Double): String {
    val abs = kotlin.math.abs(w)
    return if (abs < 1000) "${abs.toInt()} W" else String.format(java.util.Locale.US, "%.1f kW", abs / 1000)
}

/** Energie: 850 Wh, 16.6 kWh */
fun formatEnergy(wh: Double): String =
    if (wh < 1000) "${wh.toInt()} Wh" else String.format(java.util.Locale.US, "%.1f kWh", wh / 1000)

/** Sehr kompakt für Complications: 850, 2.4k */
fun formatPowerShort(w: Double): String {
    val abs = kotlin.math.abs(w)
    return if (abs < 1000) "${abs.toInt()}" else String.format(java.util.Locale.US, "%.1fk", abs / 1000)
}
