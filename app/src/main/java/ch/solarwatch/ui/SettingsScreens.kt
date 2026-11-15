package ch.solarwatch.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import ch.solarwatch.R
import ch.solarwatch.data.AppIcon
import ch.solarwatch.data.CHART_HOURS_OPTIONS
import ch.solarwatch.data.ChartMode
import ch.solarwatch.data.ChartSeries
import ch.solarwatch.data.DisplaySettings
import ch.solarwatch.data.Metric
import ch.solarwatch.data.SettingsStore
import ch.solarwatch.data.SlotLayout
import ch.solarwatch.data.masked

/** Ziel einer Werte-Anordnung */
enum class LayoutTarget(val title: String) {
    APP("Werte in der App"),
    TILE("Werte in der Kachel"),
}

private fun DisplaySettings.layout(t: LayoutTarget) = if (t == LayoutTarget.APP) app else tile
private fun DisplaySettings.withLayout(t: LayoutTarget, l: SlotLayout) =
    if (t == LayoutTarget.APP) copy(app = l) else copy(tile = l)

// ------------------------------------------------------------------ Grundgerüst

@Composable
private fun SettingsList(content: ScalingLazyListScope.() -> Unit) {
    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            content = content,
        )
    }
}

private fun ScalingLazyListScope.header(text: String) {
    item { ListHeader { Text(text, textAlign = TextAlign.Center) } }
}

private fun ScalingLazyListScope.note(text: String) {
    item {
        Text(
            text,
            fontSize = 12.sp,
            color = InkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun NavChip(
    label: String,
    secondary: String? = null,
    icon: Int? = null,
    tint: Color = InkPrimary,
    onClick: () -> Unit,
) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        secondaryLabel = secondary?.let { { Text(it, maxLines = 1) } },
        icon = icon?.let { { Icon(painterResource(it), null, Modifier.size(ChipDefaults.IconSize), tint = tint) } },
        colors = ChipDefaults.secondaryChipColors(),
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, secondary: String? = null, onChange: (Boolean) -> Unit) {
    ToggleChip(
        modifier = Modifier.fillMaxWidth(),
        checked = checked,
        onCheckedChange = onChange,
        label = { Text(label, maxLines = 1) },
        secondaryLabel = secondary?.let { { Text(it, maxLines = 1) } },
        toggleControl = { Switch(checked = checked) },
    )
}

@Composable
private fun settingsState() = SettingsStore.display.collectAsStateWithLifecycle()

// ------------------------------------------------------------------ Hauptmenü

@Composable
fun SettingsScreen(
    onLayout: (LayoutTarget) -> Unit,
    onChart: () -> Unit,
    onIcons: () -> Unit,
    onComplication: () -> Unit,
    onAccess: () -> Unit,
) {
    val s by settingsState()
    val creds by SettingsStore.credentials.collectAsStateWithLifecycle()
    SettingsList {
        header("Einstellungen")
        item { NavChip("Werte", "Anordnung in der App", R.drawable.ic_home, onClick = { onLayout(LayoutTarget.APP) }) }
        item {
            NavChip(
                "Diagramm",
                if (s.chartSeries.isEmpty()) "aus" else "${s.chartMode.label} · ${s.chartHours} h",
                R.drawable.ic_today,
                onClick = onChart,
            )
        }
        item { NavChip("Symbole", "Symbol pro Wert", R.drawable.ic_sunny, onClick = onIcons) }

        header("Kachel & Zifferblatt")
        item { NavChip("Kachel", "Werte der Kachel", R.drawable.ic_bolt, onClick = { onLayout(LayoutTarget.TILE) }) }
        item {
            NavChip(
                "Complication",
                s.complication.label,
                s.iconFor(s.complication).res,
                Color(s.complication.color),
                onClick = onComplication,
            )
        }

        header("Verbindung")
        item {
            NavChip(
                "API-Zugang",
                when {
                    !creds.isConfigured -> "fehlt"
                    creds.fromDevice -> "per Handy gesetzt"
                    else -> "aus Build"
                },
                R.drawable.ic_power,
                onClick = onAccess,
            )
        }

        item {
            Chip(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = { SettingsStore.resetDisplay() },
                label = { Text("Anzeige zurücksetzen") },
                colors = ChipDefaults.childChipColors(),
            )
        }
    }
}

// ------------------------------------------------------------------ Werte-Anordnung

@Composable
fun LayoutScreen(target: LayoutTarget, onPick: (row: Int, index: Int) -> Unit) {
    val s by settingsState()
    val layout = s.layout(target)
    SettingsList {
        header(target.title)
        layout.rows.forEachIndexed { r, row ->
            row.forEachIndexed { i, m ->
                item {
                    NavChip(
                        label = m?.label ?: "– leer –",
                        secondary = SlotLayout.slotName(r, i),
                        icon = m?.let { s.iconFor(it).res },
                        tint = m?.let { Color(it.color) } ?: InkMuted,
                        onClick = { onPick(r, i) },
                    )
                }
            }
        }
        note("Werte ohne Daten (z.B. keine Batterie) werden automatisch ausgeblendet.")
    }
}

@Composable
fun MetricPickerScreen(target: LayoutTarget, row: Int, index: Int, onDone: () -> Unit) {
    val s by settingsState()
    val current = s.layout(target).rows.getOrNull(row)?.getOrNull(index)
    SettingsList {
        header(SlotLayout.slotName(row, index))
        item {
            NavChip(if (current == null) "✓ leer" else "leer", icon = null) {
                SettingsStore.updateDisplay { it.withLayout(target, it.layout(target).with(row, index, null)) }
                onDone()
            }
        }
        Metric.entries.forEach { m ->
            item {
                NavChip(
                    label = (if (m == current) "✓ " else "") + m.label,
                    icon = s.iconFor(m).res,
                    tint = Color(m.color),
                ) {
                    SettingsStore.updateDisplay { it.withLayout(target, it.layout(target).with(row, index, m)) }
                    onDone()
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Diagramm

@Composable
fun ChartSettingsScreen() {
    val s by settingsState()
    SettingsList {
        header("Diagramm")
        ChartSeries.entries.forEach { series ->
            item {
                Toggle(series.label, series in s.chartSeries) { on ->
                    SettingsStore.updateDisplay { d ->
                        // Reihenfolge der Kurven bleibt fix (PV, Verbrauch, Wasser)
                        val set = if (on) d.chartSeries + series else d.chartSeries - series
                        d.copy(chartSeries = ChartSeries.entries.filter { it in set })
                    }
                }
            }
        }
        item {
            NavChip("Darstellung", s.chartMode.label, R.drawable.ic_today) {
                SettingsStore.updateDisplay {
                    it.copy(chartMode = if (it.chartMode == ChartMode.COMBINED) ChartMode.SEPARATE else ChartMode.COMBINED)
                }
            }
        }
        item {
            NavChip("Zeitraum", "${s.chartHours} Stunden", R.drawable.ic_pause_circle) {
                SettingsStore.updateDisplay {
                    val i = CHART_HOURS_OPTIONS.indexOf(it.chartHours)
                    it.copy(chartHours = CHART_HOURS_OPTIONS[(i + 1) % CHART_HOURS_OPTIONS.size])
                }
            }
        }
        note("Alle Kurven aus = kein Diagramm, die Werte rücken zusammen.")
    }
}

// ------------------------------------------------------------------ Symbole

@Composable
fun IconsScreen(onPick: (Metric) -> Unit) {
    val s by settingsState()
    SettingsList {
        header("Symbole")
        Metric.entries.filter { it.iconConfigurable }.forEach { m ->
            item {
                NavChip(m.label, s.iconFor(m).label, s.iconFor(m).res, Color(m.color)) { onPick(m) }
            }
        }
        note("Wärmepumpe: Symbol zeigt den Zustand (Flamme = Heizen, Dusche = Warmwasser).")
    }
}

@Composable
fun IconPickerScreen(metric: Metric, onDone: () -> Unit) {
    val s by settingsState()
    val current = s.iconFor(metric)
    SettingsList {
        header(metric.label)
        AppIcon.entries.forEach { icon ->
            item {
                NavChip(
                    label = (if (icon == current) "✓ " else "") + icon.label,
                    icon = icon.res,
                    tint = Color(metric.color),
                ) {
                    SettingsStore.updateDisplay {
                        val icons = if (icon == metric.defaultIcon) it.icons - metric else it.icons + (metric to icon)
                        it.copy(icons = icons)
                    }
                    onDone()
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Complication

@Composable
fun ComplicationScreen(onDone: () -> Unit) {
    val s by settingsState()
    SettingsList {
        header("Complication")
        Metric.entries.forEach { m ->
            item {
                NavChip(
                    label = (if (m == s.complication) "✓ " else "") + m.label,
                    icon = s.iconFor(m).res,
                    tint = Color(m.color),
                ) {
                    SettingsStore.updateDisplay { it.copy(complication = m) }
                    onDone()
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Zugang

@Composable
fun AccessScreen() {
    val creds by SettingsStore.credentials.collectAsStateWithLifecycle()
    SettingsList {
        header("API-Zugang")
        item {
            NavChip(
                "API-Key",
                if (creds.apiKey.isBlank()) "fehlt" else creds.apiKey.masked(),
                R.drawable.ic_power,
                onClick = {},
            )
        }
        item {
            NavChip("smId", if (creds.smId.isBlank()) "fehlt" else creds.smId.masked(), R.drawable.ic_home, onClick = {})
        }
        note(
            if (creds.fromDevice) "Gesetzt über die Handy-App."
            else if (creds.isConfigured) "Aus local.properties (beim Bauen)."
            else "Noch kein Zugang gespeichert.",
        )
        note("Ändern: SolarWatch-App auf dem Handy öffnen, Schlüssel einfügen und „An Uhr senden“ tippen.")
        if (creds.fromDevice) {
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { SettingsStore.clearCredentials() },
                    label = { Text("Zugang löschen") },
                    secondaryLabel = { Text("zurück zu Build-Werten") },
                    colors = ChipDefaults.childChipColors(),
                )
            }
        }
    }
}
