package ch.solarwatch.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                SetupScreen()
            }
        }
    }
}

@Composable
private fun SetupScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Schlüssel bewusst NICHT gespeichert – das Handy ist nur der Überbringer
    var apiKey by rememberSaveable { mutableStateOf("") }
    var smId by rememberSaveable { mutableStateOf("") }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SendResult?>(null) }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("SolarWatch einrichten", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Den API-Schlüssel findest du in der Solar Manager Web-App unter Profil → " +
                    "Cloud-API-Schlüssel. Erstelle ihn ohne „Erneuerung erlauben“. " +
                    "Die smId steht in der Web-App unter Gateway-Info.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it.trim(); result = null },
                label = { Text("API-Key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                trailingIcon = {
                    TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Verbergen" else "Zeigen") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = smId,
                onValueChange = { smId = it.trim(); result = null },
                label = { Text("Solar Manager ID (smId)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    busy = true
                    result = null
                    scope.launch {
                        result = verifyAndSend(context, apiKey, smId)
                        busy = false
                    }
                },
                enabled = !busy && apiKey.isNotBlank() && smId.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Prüfen & an Uhr senden")
            }

            when (val r = result) {
                is SendResult.Ok -> Text(
                    "✓ Gespeichert auf „${r.watchName}“." +
                        if (r.verified) "" else "\nHinweis: Schlüssel konnte nicht online geprüft werden.",
                    color = MaterialTheme.colorScheme.primary,
                )
                is SendResult.Error -> Text(r.message, color = MaterialTheme.colorScheme.error)
                null -> {}
            }

            Text(
                "Die Daten werden über die verschlüsselte Wear-OS-Verbindung nur an die " +
                    "SolarWatch-App auf deiner Uhr übertragen und auf dem Handy nicht gespeichert.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
