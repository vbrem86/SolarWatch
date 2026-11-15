package ch.solarwatch.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import ch.solarwatch.BuildConfig
import ch.solarwatch.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

// ------------------------------------------------------------------ Farben

/** Gemeinsame Farben (ARGB) für App, Kachel und Einstellungen. */
object Palette {
    const val YELLOW = 0xFFFFC107.toInt()
    const val BLUE = 0xFF64B5F6.toInt()
    const val GRID_RED = 0xFFE57373.toInt()
    const val GRID_GREEN = 0xFF81C784.toInt()
    const val TEAL = 0xFF4DD0E1.toInt()
    const val ORANGE = 0xFFFF8A65.toInt()
    const val HEATING_RED = 0xFFEF5350.toInt()
    const val INK = 0xFFEEEEEE.toInt()
    const val MUTED = 0xFF9E9E9E.toInt()
}

// ------------------------------------------------------------------ Symbole

/** Auswählbare Symbole. Der Enum-Name dient auch als Ressourcen-ID in der Kachel. */
enum class AppIcon(@DrawableRes val res: Int, val label: String) {
    SUN(R.drawable.ic_sunny, "Sonne"),
    HOME(R.drawable.ic_home, "Haus"),
    TODAY(R.drawable.ic_today, "Kalender"),
    BOLT(R.drawable.ic_bolt, "Blitz"),
    PLUG(R.drawable.ic_power, "Stecker"),
    BATTERY(R.drawable.ic_battery, "Batterie"),
    THERMO(R.drawable.ic_device_thermostat, "Thermometer"),
    DROP(R.drawable.ic_water_drop, "Tropfen"),
    SHOWER(R.drawable.ic_shower, "Dusche"),
    FIRE(R.drawable.ic_local_fire_department, "Flamme"),
    HEAT_PUMP(R.drawable.ic_heat_pump, "Wärmepumpe"),
    PAUSE(R.drawable.ic_pause_circle, "Pause"),
}

// ------------------------------------------------------------------ Werte

/** Ein anzeigbarer Messwert. */
enum class Metric(
    val label: String,
    /** Kurztitel für die Complication */
    val short: String,
    val defaultIcon: AppIcon,
    val color: Int,
    /** Zustand der Wärmepumpe wählt sein Symbol selbst */
    val iconConfigurable: Boolean = true,
) {
    PV("PV-Leistung", "PV", AppIcon.SUN, Palette.YELLOW),
    CONSUMPTION("Verbrauch", "Haus", AppIcon.HOME, Palette.BLUE),
    TODAY_PV("PV heute", "Heute", AppIcon.TODAY, Palette.YELLOW),
    GRID("Netz", "Netz", AppIcon.PLUG, Palette.GRID_RED),
    BATTERY_SOC("Batterie Ladestand", "Akku", AppIcon.BATTERY, Palette.TEAL),
    BATTERY_POWER("Batterie Leistung", "Akku", AppIcon.BATTERY, Palette.TEAL),
    OUTSIDE_TEMP("Aussentemperatur", "Aussen", AppIcon.THERMO, Palette.INK),
    HP_STATE("Wärmepumpe Zustand", "WP", AppIcon.HEAT_PUMP, Palette.MUTED, iconConfigurable = false),
    WATER_TEMP("Wassertemperatur", "Wasser", AppIcon.DROP, Palette.ORANGE),
}

// ------------------------------------------------------------------ Diagramm

enum class ChartSeries(val label: String, val color: Int, val isTemperature: Boolean) {
    PV("PV", Palette.YELLOW, false),
    CONSUMPTION("Verbrauch", Palette.BLUE, false),
    WATER("Wasser", Palette.ORANGE, true),
}

enum class ChartMode(val label: String) {
    COMBINED("Übereinander"),
    SEPARATE("Getrennt"),
}

val CHART_HOURS_OPTIONS = listOf(6, 12, 24)

// ------------------------------------------------------------------ Layout

/** Drei Zeilen mit 2 / 1 / 3 Plätzen; null = leer. */
data class SlotLayout(
    val top: List<Metric?>,
    val middle: List<Metric?>,
    val bottom: List<Metric?>,
) {
    val rows: List<List<Metric?>> get() = listOf(top, middle, bottom)

    fun with(row: Int, index: Int, metric: Metric?): SlotLayout {
        fun List<Metric?>.set() = toMutableList().also { it[index] = metric }
        return when (row) {
            0 -> copy(top = top.set())
            1 -> copy(middle = middle.set())
            else -> copy(bottom = bottom.set())
        }
    }

    companion object {
        val ROW_SIZES = listOf(2, 1, 3)
        val ROW_NAMES = listOf("Oben", "Mitte", "Unten")

        val DEFAULT = SlotLayout(
            top = listOf(Metric.PV, Metric.CONSUMPTION),
            middle = listOf(Metric.TODAY_PV),
            bottom = listOf(Metric.OUTSIDE_TEMP, Metric.HP_STATE, Metric.WATER_TEMP),
        )

        fun slotName(row: Int, index: Int): String {
            val pos = when (ROW_SIZES[row]) {
                1 -> ""
                2 -> listOf(" links", " rechts")[index]
                else -> listOf(" links", " Mitte", " rechts")[index]
            }
            return ROW_NAMES[row] + pos
        }
    }
}

data class DisplaySettings(
    val app: SlotLayout = SlotLayout.DEFAULT,
    val tile: SlotLayout = SlotLayout.DEFAULT,
    val icons: Map<Metric, AppIcon> = emptyMap(),
    val chartSeries: List<ChartSeries> = ChartSeries.entries,
    val chartMode: ChartMode = ChartMode.COMBINED,
    val chartHours: Int = 24,
    val complication: Metric = Metric.PV,
) {
    fun iconFor(m: Metric): AppIcon = icons[m] ?: m.defaultIcon

    // --------------------------------------------------------- JSON

    fun toJson(): String = JSONObject()
        .put("app", app.toJson())
        .put("tile", tile.toJson())
        .put("icons", JSONObject().apply { icons.forEach { (m, i) -> put(m.name, i.name) } })
        .put("chartSeries", JSONArray(chartSeries.map { it.name }))
        .put("chartMode", chartMode.name)
        .put("chartHours", chartHours)
        .put("complication", complication.name)
        .toString()

    companion object {
        fun fromJson(s: String?): DisplaySettings {
            if (s.isNullOrBlank()) return DisplaySettings()
            return runCatching {
                val o = JSONObject(s)
                val def = DisplaySettings()
                DisplaySettings(
                    app = o.optJSONObject("app")?.toLayout() ?: def.app,
                    tile = o.optJSONObject("tile")?.toLayout() ?: def.tile,
                    icons = o.optJSONObject("icons")?.let { io ->
                        io.keys().asSequence().mapNotNull { k ->
                            val m = enumOrNull<Metric>(k) ?: return@mapNotNull null
                            val i = enumOrNull<AppIcon>(io.optString(k)) ?: return@mapNotNull null
                            m to i
                        }.toMap()
                    } ?: def.icons,
                    chartSeries = o.optJSONArray("chartSeries")?.let { a ->
                        (0 until a.length()).mapNotNull { enumOrNull<ChartSeries>(a.optString(it)) }
                    } ?: def.chartSeries,
                    chartMode = enumOrNull<ChartMode>(o.optString("chartMode")) ?: def.chartMode,
                    chartHours = o.optInt("chartHours", def.chartHours).takeIf { it in CHART_HOURS_OPTIONS }
                        ?: def.chartHours,
                    complication = enumOrNull<Metric>(o.optString("complication")) ?: def.complication,
                )
            }.getOrDefault(DisplaySettings())
        }

        private fun SlotLayout.toJson() = JSONObject()
            .put("top", JSONArray(top.map { it?.name ?: "" }))
            .put("middle", JSONArray(middle.map { it?.name ?: "" }))
            .put("bottom", JSONArray(bottom.map { it?.name ?: "" }))

        private fun JSONObject.toLayout(): SlotLayout {
            fun row(key: String, size: Int, def: List<Metric?>): List<Metric?> {
                val a = optJSONArray(key) ?: return def
                return List(size) { i -> if (i < a.length()) enumOrNull<Metric>(a.optString(i)) else null }
            }
            val d = SlotLayout.DEFAULT
            return SlotLayout(
                top = row("top", SlotLayout.ROW_SIZES[0], d.top),
                middle = row("middle", SlotLayout.ROW_SIZES[1], d.middle),
                bottom = row("bottom", SlotLayout.ROW_SIZES[2], d.bottom),
            )
        }

        private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
            name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }
    }
}

// ------------------------------------------------------------------ Zugang

data class Credentials(
    val apiKey: String,
    val smId: String,
    /** true = per Handy-App gesetzt, false = aus local.properties (Build) */
    val fromDevice: Boolean,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank() && smId.isNotBlank()
}

/** Zeigt nur die letzten Zeichen, z.B. "••••a1b2". */
fun String.masked(visible: Int = 4): String =
    if (isBlank()) "–" else "••••" + takeLast(visible)

// ------------------------------------------------------------------ Speicher

/**
 * Einstellungen in privaten SharedPreferences (allowBackup=false, nicht im Backup).
 * Wird in [ch.solarwatch.SolarApp] initialisiert und ist dadurch auch in Kachel
 * und Complication verfügbar.
 */
object SettingsStore {
    private const val PREFS = "solarwatch_settings"
    private const val KEY_DISPLAY = "display"
    private const val KEY_API = "apiKey"
    private const val KEY_SMID = "smId"

    private lateinit var prefs: SharedPreferences
    private var onChanged: () -> Unit = {}

    private val _display = MutableStateFlow(DisplaySettings())
    val display: StateFlow<DisplaySettings> = _display.asStateFlow()

    private val _credentials = MutableStateFlow(buildCredentials())
    val credentials: StateFlow<Credentials> = _credentials.asStateFlow()

    @Synchronized
    fun init(context: Context, onChanged: () -> Unit) {
        if (::prefs.isInitialized) return
        this.onChanged = onChanged
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _display.value = DisplaySettings.fromJson(prefs.getString(KEY_DISPLAY, null))
        _credentials.value = loadCredentials()
    }

    @Synchronized
    fun updateDisplay(transform: (DisplaySettings) -> DisplaySettings) {
        val next = transform(_display.value)
        if (next == _display.value) return
        _display.value = next
        prefs.edit().putString(KEY_DISPLAY, next.toJson()).apply()
        onChanged()
    }

    fun resetDisplay() = updateDisplay { DisplaySettings() }

    @SuppressLint("ApplySharedPref")
    @Synchronized
    fun setCredentials(apiKey: String, smId: String) {
        prefs.edit().putString(KEY_API, apiKey.trim()).putString(KEY_SMID, smId.trim()).commit()
        applyCredentials(loadCredentials())
    }

    @SuppressLint("ApplySharedPref")
    @Synchronized
    fun clearCredentials() {
        prefs.edit().remove(KEY_API).remove(KEY_SMID).commit()
        applyCredentials(loadCredentials())
    }

    private fun applyCredentials(c: Credentials) {
        _credentials.value = c
        SolarManagerApi.resetCache()
        onChanged()
    }

    private fun loadCredentials(): Credentials {
        val key = prefs.getString(KEY_API, null).orEmpty()
        val id = prefs.getString(KEY_SMID, null).orEmpty()
        return if (key.isNotBlank() && id.isNotBlank()) Credentials(key, id, fromDevice = true) else buildCredentials()
    }

    private fun buildCredentials() = Credentials(BuildConfig.SM_API_KEY, BuildConfig.SM_ID, fromDevice = false)
}
