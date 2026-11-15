# SolarWatch – Solar Manager für Wear OS

Standalone Wear OS App (Pixel Watch 4) mit
- **App**: PV-Produktion, Verbrauch, Netzbezug/Einspeisung, Batterie (SOC + Lade-/Entladeleistung), Polling alle 10 s
- **Tile**: Übersicht zum Wischen, Tippen öffnet die App
- **Complication**: aktuelle PV-Leistung fürs Zifferblatt

## Module
- `app` – Wear-OS-App (Uhr) mit App, Kachel und Complication
- `mobile` – kleine Begleit-App fürs Android-Handy, nur zum Übertragen von API-Key und smId

## Einrichten
1. Projekt in Android Studio öffnen (Gradle-Sync).
2. Uhr: Einstellungen → System → Info → Build-Nummer 7× tippen → Entwickleroptionen → *ADB-Debugging* + *Debugging über WLAN* aktivieren.
3. In Android Studio: *Pair Devices Using Wi-Fi* → Uhr auswählen → Run-Konfiguration **app** → ▶ Run.
4. Handy per USB/WLAN verbinden → Run-Konfiguration **mobile** → ▶ Run.
5. Auf dem Handy die App *SolarWatch* öffnen, API-Key und smId einfügen → **Prüfen & an Uhr senden**.

Beide Apps müssen mit **demselben Schlüssel signiert** sein (beim Bauen auf demselben Rechner automatisch der Fall), sonst stellt Wear OS die Nachricht nicht zu.

Alternative ohne Handy-App: `local.properties.example` nach `local.properties` kopieren und ausfüllen. Diese Werte werden beim Bauen eingebettet und gelten, solange per Handy nichts gesetzt wurde.

## Einstellungen auf der Uhr
Zahnrad unten auf dem Hauptbildschirm:
- **Werte** – welche Messwerte oben (2), in der Mitte (1) und unten (3) stehen
- **Diagramm** – Kurven PV / Verbrauch / Wasser ein/aus, *übereinander* oder *getrennt*, Zeitraum 6/12/24 h
- **Symbole** – Symbol pro Messwert
- **Kachel** – eigene Werte-Anordnung für die Kachel
- **Complication** – welcher Wert aufs Zifferblatt kommt
- **API-Zugang** – Status (maskiert), Zugang löschen

## API
- Auth: Header `X-API-KEY: <API-Key>` (Schlüssel ohne „Erneuerung erlauben“ erstellen!)
- `GET  https://cloud.solar-manager.ch/v3/users/{smId}/data/stream`
  Felder: `pW` PV, `cW` Verbrauch, `iW`/`eW` Netz Import/Export, `bcW`/`bdW` Batterie Laden/Entladen, `soc`
