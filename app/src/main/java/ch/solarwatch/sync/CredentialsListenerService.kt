package ch.solarwatch.sync

import android.util.Log
import ch.solarwatch.data.SettingsStore
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

/**
 * Empfängt API-Key und smId von der Handy-App über die Wear-OS-Datenschicht.
 * Nachrichten kommen nur von Apps mit gleichem Paketnamen UND gleicher Signatur
 * (vom System erzwungen) – fremde Apps können hier nichts einspielen.
 */
class CredentialsListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PATH_CREDENTIALS) return
        val ok = runCatching {
            val o = JSONObject(String(event.data, Charsets.UTF_8))
            val key = o.optString("apiKey").trim()
            val smId = o.optString("smId").trim()
            require(key.isNotEmpty() && smId.isNotEmpty())
            SettingsStore.setCredentials(key, smId)
        }.onFailure { Log.w(TAG, "Ungültige Zugangsdaten empfangen", it) }.isSuccess

        Wearable.getMessageClient(this)
            .sendMessage(event.sourceNodeId, PATH_ACK, byteArrayOf(if (ok) 1 else 0))
    }

    companion object {
        private const val TAG = "SolarWatchSync"
        const val PATH_CREDENTIALS = "/solarwatch/credentials"
        const val PATH_ACK = "/solarwatch/credentials/ack"
    }
}
