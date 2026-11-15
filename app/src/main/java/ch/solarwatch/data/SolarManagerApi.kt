package ch.solarwatch.data

import ch.solarwatch.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class SolarManagerException(message: String) : IOException(message)

/**
 * Minimaler Client für die Solar Manager Cloud API.
 *
 * Auth:   Header "X-API-KEY: <API-Key>" bei jeder Anfrage (kein Token-Tausch).
 *         Der Schlüssel muss OHNE "Erneuerung erlauben" erstellt sein, sonst rotiert er.
 * Daten:  GET /v3/users/{smId}/data/stream
 * Limits: 1000 Stream-Abfragen pro Stunde, bei Überschreitung HTTP 429.
 */
object SolarManagerApi {
    private const val BASE = "https://cloud.solar-manager.ch"
    private const val TIMEOUT_MS = 15_000

    private val apiKey = BuildConfig.SM_API_KEY
    private val smId = BuildConfig.SM_ID

    @Volatile var lastData: SolarData? = null
        private set

    @Volatile var lastHistory: History? = null
        private set

    /** Zwischenspeicher für [todayPvWh] (Zeitpunkt, Wert) */
    @Volatile private var todayCache: Pair<Long, Double>? = null

    val isConfigured: Boolean get() = apiKey.isNotBlank() && smId.isNotBlank()

    suspend fun fetchStream(): SolarData = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            throw SolarManagerException("API-Key / smId fehlen in local.properties")
        }
        parse(get("/v3/users/$smId/data/stream")).also { lastData = it }
    }

    /**
     * Verlauf der letzten [hours] Stunden in 15-Minuten-Schritten:
     *  - PV & Verbrauch:  GET /v3/users/{smId}/data/range
     *  - Wassertemperatur: GET /v3/devices/{heatPumpId}/data/range
     */
    suspend fun fetchHistory(hours: Long = 24): History = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            throw SolarManagerException("API-Key / smId fehlen in local.properties")
        }
        val to = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        val from = to.minus(java.time.Duration.ofHours(hours))
        val range = "from=$from&to=$to&interval=900"

        val main = get("/v3/users/$smId/data/range?$range").optJSONArray("data")
        val pv = ArrayList<Pair<Long, Double>>()
        val cons = ArrayList<Pair<Long, Double>>()
        val midnight = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        var todayPvWh = 0.0
        if (main != null) {
            for (i in 0 until main.length()) {
                val o = main.optJSONObject(i) ?: continue
                val t = parseTime(o) ?: continue
                o.numOrNull("pW")?.let { pv += t to it }
                o.numOrNull("cW")?.let { cons += t to it }
                // Intervall-Energie; Zeitstempel = Beginn des Intervalls
                if (t >= midnight) todayPvWh += o.numOrNull("pWh") ?: 0.0
            }
        }

        val hpId = (lastData ?: fetchStream()).heatPump?.id
        val water = hpId?.let { id ->
            val arr = get("/v3/devices/$id/data/range?$range").optJSONArray("data")
            val pts = ArrayList<Pair<Long, Double>>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val t = parseTime(o) ?: continue
                    o.numOrNull("temperature")?.let { pts += t to it }
                }
            }
            Series(pts)
        }

        todayCache = System.currentTimeMillis() to todayPvWh
        History(
            fromMillis = from.toEpochMilli(),
            toMillis = to.toEpochMilli(),
            pvW = Series(pv),
            consumptionW = Series(cons),
            waterTempC = water,
            todayPvWh = todayPvWh,
        ).also { lastHistory = it }
    }

    /**
     * PV-Produktion seit Mitternacht in Wh (für Tile).
     * Nutzt einen max. 5 min alten Wert, sonst GET /v3/users/{smId}/data/range ab Mitternacht.
     */
    suspend fun todayPvWh(): Double = withContext(Dispatchers.IO) {
        todayCache?.let { (at, v) -> if (System.currentTimeMillis() - at < 5 * 60_000) return@withContext v }
        val from = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        val to = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        val arr = get("/v3/users/$smId/data/range?from=$from&to=$to&interval=900").optJSONArray("data")
        var sum = 0.0
        if (arr != null) {
            for (i in 0 until arr.length()) sum += arr.optJSONObject(i)?.numOrNull("pWh") ?: 0.0
        }
        todayCache = System.currentTimeMillis() to sum
        sum
    }

    private fun parseTime(o: JSONObject): Long? =
        runCatching { java.time.Instant.parse(o.getString("t")).toEpochMilli() }.getOrNull()

    private fun JSONObject.numOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeUnless { it.isNaN() } else null

    // ---------------------------------------------------------------- parsing

    private fun parse(o: JSONObject): SolarData {
        fun num(key: String): Double? =
            if (o.has(key) && !o.isNull(key)) o.optDouble(key).takeUnless { it.isNaN() } else null

        val pW = num("pW") ?: 0.0
        val cW = num("cW") ?: 0.0
        val bcW = num("bcW")
        val bdW = num("bdW")
        val iW = num("iW")
        val eW = num("eW")

        // Batterie nur anzeigen, wenn ein Ladezustand geliefert wird
        // (ohne Batterie sendet die API bcW/bdW = 0, aber kein soc)
        val soc = num("soc")
        val batteryW = if (soc != null) (bcW ?: 0.0) - (bdW ?: 0.0) else null
        val gridW = if (iW != null || eW != null) {
            (iW ?: 0.0) - (eW ?: 0.0)
        } else {
            // Bilanz: Verbrauch + Laden - PV - Entladen
            cW + (bcW ?: 0.0) - pW - (bdW ?: 0.0)
        }

        return SolarData(
            pvW = pW,
            consumptionW = cW,
            gridW = gridW,
            batteryW = batteryW,
            soc = soc,
            heatPump = parseHeatPump(o),
            timestamp = o.optString("t").ifBlank { null },
        )
    }

    /** Wärmepumpe = Gerät im Stream mit operationState bzw. Aussentemperatur. */
    private fun parseHeatPump(o: JSONObject): HeatPumpData? {
        val devices = o.optJSONArray("devices") ?: return null
        for (i in 0 until devices.length()) {
            val d = devices.optJSONObject(i) ?: continue
            if (!d.has("operationState") && !d.has("temperatureOutside")) continue
            fun num(key: String): Double? =
                if (d.has(key) && !d.isNull(key)) d.optDouble(key).takeUnless { it.isNaN() } else null
            return HeatPumpData(
                id = d.optString("_id").ifBlank { null },
                waterTempC = num("temperature"),
                outsideTempC = num("temperatureOutside"),
                operationState = num("operationState")?.toInt(),
            )
        }
        return null
    }

    // ------------------------------------------------------------------- http

    private fun get(path: String): JSONObject {
        val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-API-KEY", apiKey)
        }
        try {
            when (val code = conn.responseCode) {
                in 200..299 -> return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                401, 403 -> throw SolarManagerException(
                    "API-Schlüssel ungültig. Neuen Schlüssel (ohne \"Erneuerung erlauben\") " +
                        "erzeugen und in local.properties eintragen.",
                )
                429 -> throw SolarManagerException("Zu viele Abfragen – bitte später erneut versuchen.")
                else -> {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw SolarManagerException("HTTP $code bei $path ${err.take(120)}")
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}
