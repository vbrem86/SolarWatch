package ch.solarwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import ch.solarwatch.R
import ch.solarwatch.data.DisplaySettings
import ch.solarwatch.data.Metric
import ch.solarwatch.data.Palette
import ch.solarwatch.data.SettingsStore
import ch.solarwatch.data.SolarData
import ch.solarwatch.data.isAvailable
import ch.solarwatch.data.view

val GridRed = Color(Palette.GRID_RED)

/**
 * Ein Bildschirm ohne Scrollen, Inhalt laut Einstellungen:
 *  oben   – 2 Werte (gross)
 *  Mitte  – 1 Wert
 *  dann   – Verlauf (übereinander oder getrennt)
 *  unten  – 3 Werte
 *  Zahnrad öffnet die Einstellungen.
 */
@Composable
fun SolarScreen(onOpenSettings: () -> Unit, vm: SolarViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val hist by vm.history.collectAsStateWithLifecycle()
    val settings by SettingsStore.display.collectAsStateWithLifecycle()
    val creds by SettingsStore.credentials.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.startPolling() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.stopPolling() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val d = state.data
        val sidePad = maxWidth * 0.07f
        if (d == null) {
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = when {
                        !creds.isConfigured -> "API-Zugang fehlt.\nSolarWatch-App auf dem Handy öffnen und Schlüssel senden."
                        state.error != null -> state.error!!
                        else -> "Lade Daten…"
                    },
                    color = if (state.error != null || !creds.isConfigured) GridRed else InkPrimary,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                )
                if (state.error != null || !creds.isConfigured) {
                    Spacer(Modifier.height(8.dp))
                    Chip(
                        onClick = onOpenSettings,
                        label = { Text("Einstellungen") },
                        icon = { Icon(painterResource(R.drawable.ic_settings), null, Modifier.size(20.dp)) },
                        colors = ChipDefaults.secondaryChipColors(),
                    )
                }
            }
            return@BoxWithConstraints
        }

        val today = hist.history?.todayPvWh
        val rows = settings.app.rows.map { row -> row.filter { it != null && it.isAvailable(d) }.filterNotNull() }
        val showChart = settings.chartSeries.isNotEmpty()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = maxHeight * 0.10f, bottom = maxHeight * 0.05f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (showChart) Arrangement.Top else Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            MetricRow(rows[0], d, today, settings, 17.sp, bold = true)
            MetricRow(rows[1], d, today, settings, 13.sp)

            if (showChart) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = sidePad),
                    contentAlignment = Alignment.Center,
                ) {
                    val h = hist.history
                    if (h != null) {
                        HistoryCharts(h, settings.chartSeries, settings.chartMode, Modifier.fillMaxSize())
                    } else {
                        Text(
                            hist.error ?: "Lade Verlauf…",
                            fontSize = 12.sp,
                            color = if (hist.error != null) GridRed else InkMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            MetricRow(rows[2], d, today, settings, 15.sp)

            // Zahnrad + Offline-Hinweis
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = "Einstellungen",
                    tint = InkMuted,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onOpenSettings)
                        .padding(4.dp)
                        .size(16.dp),
                )
                if (state.error != null) Text("⚠ offline", fontSize = 10.sp, color = GridRed)
            }
        }
    }
}

@Composable
private fun MetricRow(
    metrics: List<Metric>,
    d: SolarData,
    todayWh: Double?,
    settings: DisplaySettings,
    size: TextUnit,
    bold: Boolean = false,
) {
    if (metrics.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (metrics.size > 2) 10.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        metrics.forEach { m -> MetricItem(m, d, todayWh, settings, size, bold) }
    }
}

@Composable
private fun MetricItem(
    m: Metric,
    d: SolarData,
    todayWh: Double?,
    settings: DisplaySettings,
    size: TextUnit,
    bold: Boolean,
) {
    val v = m.view(d, todayWh, settings)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(v.icon.res),
            contentDescription = m.label,
            tint = Color(v.tint),
            modifier = Modifier.size((size.value + if (m == Metric.HP_STATE) 7 else 3).dp),
        )
        if (v.text.isNotEmpty()) {
            Spacer(Modifier.width(3.dp))
            Text(
                v.text,
                fontSize = size,
                color = InkPrimary,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
        v.suffix?.let { Text(it, fontSize = (size.value - 2).sp, color = InkMuted, maxLines = 1) }
    }
}
