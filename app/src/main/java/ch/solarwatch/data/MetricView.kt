package ch.solarwatch.data

import java.util.Locale
import kotlin.math.abs

/** Fertig aufbereiteter Wert für App und Kachel. */
data class MetricView(
    val icon: AppIcon,
    /** Symbolfarbe (ARGB) */
    val tint: Int,
    /** Wert; leer = nur Symbol */
    val text: String,
    /** z.B. " heute", gedämpft dargestellt */
    val suffix: String? = null,
)

/** Ob der Wert bei dieser Anlage überhaupt existiert (z.B. keine Batterie → ausblenden). */
fun Metric.isAvailable(d: SolarData): Boolean = when (this) {
    Metric.BATTERY_SOC, Metric.BATTERY_POWER -> d.soc != null
    Metric.OUTSIDE_TEMP, Metric.HP_STATE, Metric.WATER_TEMP -> d.heatPump != null
    else -> true
}

fun Metric.view(d: SolarData, todayWh: Double?, settings: DisplaySettings): MetricView {
    val icon = settings.iconFor(this)
    return when (this) {
        Metric.PV -> MetricView(icon, color, formatPower(d.pvW))
        Metric.CONSUMPTION -> MetricView(icon, color, formatPower(d.consumptionW))
        Metric.TODAY_PV -> MetricView(icon, color, todayWh?.let { formatEnergy(it) } ?: "–", " heute")
        Metric.GRID -> MetricView(
            icon,
            if (d.gridW > 0) Palette.GRID_RED else Palette.GRID_GREEN, // rot = Bezug, grün = Einspeisung
            formatPower(d.gridW),
        )
        Metric.BATTERY_SOC -> MetricView(icon, color, d.soc?.let { "${it.toInt()} %" } ?: "–")
        Metric.BATTERY_POWER -> MetricView(icon, color, d.batteryW?.let { signed(it) } ?: "–")
        Metric.OUTSIDE_TEMP -> MetricView(icon, color, tempShort(d.heatPump?.outsideTempC))
        Metric.WATER_TEMP -> MetricView(icon, color, tempShort(d.heatPump?.waterTempC))
        Metric.HP_STATE -> {
            val state = d.heatPump?.operationState
            val (i, tint) = when (state) {
                2 -> AppIcon.FIRE to Palette.HEATING_RED
                3 -> AppIcon.SHOWER to Palette.ORANGE
                else -> AppIcon.HEAT_PUMP to Palette.MUTED
            }
            // Unbekannter Zustand: Code anzeigen, damit er zugeordnet werden kann
            MetricView(i, tint, if (state != null && state !in HEAT_PUMP_STATES) "$state" else "")
        }
    }
}

/** Sehr kurzer Text für die Complication. */
fun Metric.shortText(d: SolarData?, todayWh: Double?): String {
    if (d == null) return "--"
    return when (this) {
        Metric.PV -> formatPowerShort(d.pvW)
        Metric.CONSUMPTION -> formatPowerShort(d.consumptionW)
        Metric.TODAY_PV -> todayWh?.let {
            if (it < 1000) "${it.toInt()}" else String.format(Locale.US, "%.1fk", it / 1000)
        } ?: "--"
        Metric.GRID -> (if (d.gridW < 0) "-" else "") + formatPowerShort(d.gridW)
        Metric.BATTERY_SOC -> d.soc?.let { "${it.toInt()}%" } ?: "--"
        Metric.BATTERY_POWER -> d.batteryW?.let { (if (it < 0) "-" else "") + formatPowerShort(it) } ?: "--"
        Metric.OUTSIDE_TEMP -> d.heatPump?.outsideTempC?.let { "%.0f°".format(it) } ?: "--"
        Metric.WATER_TEMP -> d.heatPump?.waterTempC?.let { "%.0f°".format(it) } ?: "--"
        Metric.HP_STATE -> d.heatPump?.stateLabel() ?: "--"
    }
}

private fun tempShort(c: Double?): String = c?.let { String.format(Locale.US, "%.1f°", it) } ?: "–"

private fun signed(w: Double): String = (if (w > 0) "+" else if (w < 0) "−" else "") + formatPower(abs(w))
