package ch.solarwatch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import ch.solarwatch.data.ChartMode
import ch.solarwatch.data.ChartSeries
import ch.solarwatch.data.History
import ch.solarwatch.data.formatPower
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Text-Farben (Text nie in Serienfarbe) */
val InkPrimary = Color(0xFFEEEEEE)
val InkMuted = Color(0xFF9E9E9E)
private val GridColor = Color(0xFF2E2E2E)

private data class ChartLine(
    val series: ChartSeries,
    val points: List<Pair<Long, Double>>,
) {
    val color get() = Color(series.color)
    val isTemp get() = series.isTemperature
}

/**
 * Verlauf der gewählten Kurven.
 *  - [ChartMode.COMBINED]: ein Diagramm, Leistung links (kW), Temperatur rechts (°C).
 *    Die Temperatur hat bewusst eine eigene Skala – Schnittpunkte haben keine Bedeutung.
 *  - [ChartMode.SEPARATE]: ein Diagramm pro Kurve, untereinander, gleiche Zeitachse.
 *
 * Antippen/Wischen zeigt die Werte zum gewählten Zeitpunkt (in allen Diagrammen gleichzeitig).
 */
@Composable
fun HistoryCharts(
    h: History,
    series: List<ChartSeries>,
    mode: ChartMode,
    modifier: Modifier = Modifier,
) {
    val lines = series.mapNotNull { s ->
        val pts = when (s) {
            ChartSeries.PV -> h.pvW.points
            ChartSeries.CONSUMPTION -> h.consumptionW.points
            ChartSeries.WATER -> h.waterTempC?.points ?: return@mapNotNull null // keine Wärmepumpe
        }
        ChartLine(s, pts)
    }
    if (lines.isEmpty()) return

    // Gewählter Zeitpunkt als Anteil 0..1 der Breite, gemeinsam für alle Diagramme
    var scrub by remember { mutableStateOf<Float?>(null) }
    val separate = mode == ChartMode.SEPARATE && lines.size > 1

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (separate) {
            lines.forEach { line ->
                ChartPanel(
                    lines = listOf(line),
                    h = h,
                    scrub = scrub,
                    onScrub = { scrub = it },
                    headerHeight = 13.dp,
                    headerFont = 10.sp,
                    modifier = Modifier.weight(1f).padding(bottom = 2.dp),
                )
            }
        } else {
            ChartPanel(lines, h, scrub, { scrub = it }, 16.dp, 11.sp, Modifier.weight(1f))
        }

        // Zeitachse etwas eingerückt, damit der runde Rand nichts abschneidet
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val hours = ((h.toMillis - h.fromMillis) / 3_600_000).toInt()
            Text("−${hours}h", fontSize = 9.sp, color = InkMuted)
            Text("−${hours / 2}h", fontSize = 9.sp, color = InkMuted)
            Text("jetzt", fontSize = 9.sp, color = InkMuted)
        }
    }
}

@Composable
private fun ChartPanel(
    lines: List<ChartLine>,
    h: History,
    scrub: Float?,
    onScrub: (Float?) -> Unit,
    headerHeight: androidx.compose.ui.unit.Dp,
    headerFont: TextUnit,
    modifier: Modifier,
) {
    val power = lines.filter { !it.isTemp }
    val temps = lines.filter { it.isTemp }

    // Leistung: 0 .. runder Maximalwert
    val pMax = niceCeil(power.flatMap { it.points }.maxOfOrNull { it.second } ?: 1000.0)
    // Temperatur: eng um die Daten, auf 5 °C gerundet
    val allT = temps.flatMap { it.points }
    val tMin = allT.minOfOrNull { it.second }?.let { floor(it / 5) * 5 } ?: 0.0
    val tMax = allT.maxOfOrNull { it.second }?.let { (ceil(it / 5) * 5).coerceAtLeast(tMin + 5) } ?: 1.0

    var widthPx by remember { mutableStateOf(1f) }
    // Gesten-Handler laufen in pointerInput(Unit) weiter -> immer aktuellen Wert lesen
    val currentScrub by rememberUpdatedState(scrub)
    val currentOnScrub by rememberUpdatedState(onScrub)
    val textMeasurer = rememberTextMeasurer()
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.GERMANY) }

    val scrubT = scrub?.let { f -> h.fromMillis + (f * (h.toMillis - h.fromMillis)).toLong() }
    val nearest = lines.map { l -> scrubT?.let { t -> l.points.minByOrNull { abs(it.first - t) } } }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Kopfzeile mit fester Höhe, damit nichts springt
        Row(
            modifier = Modifier.height(headerHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (scrubT != null) {
                val t = nearest.firstNotNullOfOrNull { it }?.first ?: scrubT
                Text(timeFmt.format(Date(t)), fontSize = headerFont, color = InkMuted)
                lines.forEachIndexed { i, l ->
                    LegendItem(l.color, nearest[i]?.let { valueText(l, it.second) } ?: "–", headerFont)
                }
            } else {
                lines.forEach { LegendItem(it.color, it.series.label, headerFont) }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { currentOnScrub(if (currentScrub == null) it.x / widthPx else null) })
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragStart = { currentOnScrub(it.x / widthPx) }) { change, _ ->
                        currentOnScrub((change.position.x / widthPx).coerceIn(0f, 1f))
                    }
                },
        ) {
            widthPx = size.width
            fun x(t: Long) = ((t - h.fromMillis).toFloat() / (h.toMillis - h.fromMillis)) * size.width
            fun yP(v: Double) = size.height - (v / pMax).toFloat() * size.height
            fun yT(v: Double) = size.height - ((v - tMin) / (tMax - tMin)).toFloat() * size.height

            // Raster
            listOf(0f, 0.5f, 1f).forEach { f ->
                val y = size.height * f
                drawLine(GridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }

            val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            lines.forEach { l -> drawSeries(l.points, ::x, if (l.isTemp) ::yT else ::yP, l.color, stroke) }

            // Skalen: links Leistung, rechts Temperatur (nach den Linien, mit Hintergrund)
            val style = TextStyle(fontSize = 9.sp, color = InkMuted)
            if (power.isNotEmpty()) label(textMeasurer, formatPower(pMax), style, left = true, y = 0f)
            if (allT.isNotEmpty()) {
                label(textMeasurer, "%.0f°".format(tMax), style, left = false, y = 0f)
                label(textMeasurer, "%.0f°".format(tMin), style, left = false, y = size.height, bottom = true)
            }

            // Fadenkreuz
            scrubT?.let { t ->
                val cx = x(nearest.firstNotNullOfOrNull { it }?.first ?: t)
                drawLine(InkMuted, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.dp.toPx())
                lines.forEachIndexed { i, l ->
                    nearest[i]?.let { p -> dot(Offset(x(p.first), if (l.isTemp) yT(p.second) else yP(p.second)), l.color) }
                }
            }
        }
    }
}

private fun valueText(l: ChartLine, v: Double) = if (l.isTemp) "%.0f°".format(v) else formatPower(v)

@Composable
private fun LegendItem(color: Color, text: String, fontSize: TextUnit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(3.dp))
        Text(text, fontSize = fontSize, color = InkPrimary, maxLines = 1)
    }
}

private fun DrawScope.drawSeries(
    pts: List<Pair<Long, Double>>,
    x: (Long) -> Float,
    y: (Double) -> Float,
    color: Color,
    stroke: Stroke,
) {
    if (pts.isEmpty()) return
    val path = Path()
    var prev: Long? = null
    pts.sortedBy { it.first }.forEach { (t, v) ->
        val gap = prev?.let { t - it > 45 * 60_000 } ?: true // Lücken > 45 min nicht verbinden
        if (gap) path.moveTo(x(t), y(v)) else path.lineTo(x(t), y(v))
        prev = t
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.label(
    tm: TextMeasurer,
    text: String,
    style: TextStyle,
    left: Boolean,
    y: Float,
    bottom: Boolean = false,
) {
    val layout = tm.measure(text, style)
    val w = layout.size.width.toFloat()
    val hgt = layout.size.height.toFloat()
    val top = if (bottom) y - hgt - 1f else y + 1f
    val lx = if (left) 0f else size.width - w - 4f
    drawRect(Color.Black.copy(alpha = 0.75f), Offset(lx, top), Size(w + 4f, hgt))
    drawText(layout, topLeft = Offset(lx + 2f, top))
}

private fun DrawScope.dot(c: Offset, color: Color) {
    drawCircle(Color.Black, radius = 4.5.dp.toPx(), center = c) // Ring zur Abgrenzung
    drawCircle(color, radius = 3.5.dp.toPx(), center = c)
}

/** Aufrunden auf 1, 1.5, 2, 2.5, 3, 4, 5, 6, 8 × 10^n */
private fun niceCeil(v: Double): Double {
    if (v <= 0) return 1000.0
    val base = Math.pow(10.0, floor(kotlin.math.log10(v)))
    val f = v / base
    val nice = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0).first { f <= it }
    return nice * base
}
