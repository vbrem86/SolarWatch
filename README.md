# SolarWatch – Solar Manager für Wear OS

Standalone Wear OS App (Pixel Watch 4) mit
- **App**: PV-Produktion, Verbrauch, Netzbezug/Einspeisung, Batterie (SOC + Lade-/Entladeleistung), Polling alle 10 s
- **Tile**: Übersicht zum Wischen, Tippen öffnet die App
- **Complication**: aktuelle PV-Leistung fürs Zifferblatt

## Einrichten
1. `local.properties.example` nach `local.properties` kopieren und ausfüllen:
   - `solarmanager.apiKey` – Solar Manager Web-App → Profil → Cloud-API-Schlüssel
   - `solarmanager.smId` – ID deines Gateways
2. Projekt in Android Studio öffnen (Gradle-Sync).
3. Uhr: Einstellungen → System → Info → Build-Nummer 7× tippen → Entwickleroptionen → *ADB-Debugging* + *Debugging über WLAN* aktivieren.
4. In Android Studio: *Pair Devices Using Wi-Fi* → Uhr auswählen → ▶ Run.

## API
- Auth: Header `X-API-KEY: <API-Key>` (Schlüssel ohne „Erneuerung erlauben“ erstellen!)
- `GET  https://cloud.solar-manager.ch/v3/users/{smId}/data/stream`
  Felder: `pW` PV, `cW` Verbrauch, `iW`/`eW` Netz Import/Export, `bcW`/`bdW` Batterie Laden/Entladen, `soc`
