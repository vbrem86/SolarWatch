package ch.solarwatch.mobile

import android.content.Context
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val PATH_CREDENTIALS = "/solarwatch/credentials"
private const val PATH_ACK = "/solarwatch/credentials/ack"

sealed interface SendResult {
    data class Ok(val watchName: String, val verified: Boolean) : SendResult
    data class Error(val message: String) : SendResult
}

/** Prüft den Schlüssel bei Solar Manager und schickt ihn dann an die Uhr. */
suspend fun verifyAndSend(context: Context, apiKey: String, smId: String): SendResult {
    val verified = when (val v = verify(apiKey, smId)) {
        Verify.Ok -> true
        Verify.Offline -> false // ohne Internet trotzdem senden
        is Verify.Invalid -> return SendResult.Error(v.message)
    }

    val nodes = runCatching { Wearable.getNodeClient(context).connectedNodes.await() }
        .getOrElse { return SendResult.Error("Wear OS nicht verfügbar: ${it.message}") }
    if (nodes.isEmpty()) return SendResult.Error("Keine Uhr verbunden. Ist die Uhr gekoppelt und in Reichweite?")

    val payload = JSONObject().put("apiKey", apiKey).put("smId", smId).toString().toByteArray()
    val client = Wearable.getMessageClient(context)
    val ack = CompletableDeferred<Pair<String, Boolean>>()
    val listener = MessageClient.OnMessageReceivedListener { e ->
        if (e.path == PATH_ACK) ack.complete(e.sourceNodeId to (e.data.firstOrNull() == 1.toByte()))
    }
    client.addListener(listener).await()
    try {
        nodes.forEach { client.sendMessage(it.id, PATH_CREDENTIALS, payload).await() }
        val (nodeId, ok) = withTimeoutOrNull(8_000) { ack.await() }
            ?: return SendResult.Error(
                "Gesendet, aber keine Antwort. Ist die aktuelle SolarWatch-Version auf der Uhr installiert?",
            )
        if (!ok) return SendResult.Error("Die Uhr hat die Daten abgelehnt.")
        return SendResult.Ok(nodes.firstOrNull { it.id == nodeId }?.displayName ?: "Uhr", verified)
    } catch (e: Exception) {
        return SendResult.Error("Senden fehlgeschlagen: ${e.message}")
    } finally {
        client.removeListener(listener)
    }
}

private sealed interface Verify {
    data object Ok : Verify
    data object Offline : Verify
    data class Invalid(val message: String) : Verify
}

private suspend fun verify(apiKey: String, smId: String): Verify = withContext(Dispatchers.IO) {
    val url = URL("https://cloud.solar-manager.ch/v3/users/${java.net.URLEncoder.encode(smId, "UTF-8")}/data/stream")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("X-API-KEY", apiKey)
    }
    try {
        when (val code = conn.responseCode) {
            in 200..299 -> Verify.Ok
            401, 403 -> Verify.Invalid("API-Key ungültig oder ohne Zugriff auf diese smId.")
            404 -> Verify.Invalid("smId nicht gefunden.")
            429 -> Verify.Offline // Limit erreicht – nicht prüfbar, trotzdem senden
            else -> Verify.Invalid("Solar Manager meldet HTTP $code.")
        }
    } catch (e: IOException) {
        Verify.Offline
    } finally {
        conn.disconnect()
    }
}
