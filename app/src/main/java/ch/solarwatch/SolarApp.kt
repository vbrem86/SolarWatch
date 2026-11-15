package ch.solarwatch

import android.app.Application
import android.content.ComponentName
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import ch.solarwatch.complication.PvComplicationService
import ch.solarwatch.data.SettingsStore
import ch.solarwatch.tile.SolarTileService

class SolarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Bei jeder Änderung der Einstellungen Kachel und Complication neu zeichnen lassen
        SettingsStore.init(this) {
            TileService.getUpdater(this).requestUpdate(SolarTileService::class.java)
            ComplicationDataSourceUpdateRequester
                .create(this, ComponentName(this, PvComplicationService::class.java))
                .requestUpdateAll()
        }
    }
}
