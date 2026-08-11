# Arkitektur

```mermaid
flowchart TB
    subgraph iOS["iosApp (SwiftUI)"]
        iosViews["Views: Dashboard · Sjekkliste · Mangler · Ressurser · Arkiv · Rediger"]
    end

    subgraph Android["androidApp (skall)"]
        activity["MainActivity\n(PDF-import, sync-trigger)"]
    end

    subgraph SharedUI["sharedUI (Compose, kun Android)"]
        composeScreens["Screens: Dashboard · Sjekkliste · Arkiv & Mangler · Ressurser · Rediger"]
        shells["Adaptivt skall (App.kt)\n< 600 dp: bunnavigasjon (telefon)\n≥ 600 dp: sidemeny (nettbrett)"]
        shells --> composeScreens
    end

    subgraph SharedLogic["sharedLogic (Kotlin Multiplatform)"]
        repo["ChecklistRepository\n(all forretningslogikk og validering)"]
        seeder["DatabaseSeeder\n(faste ID-er)"]
        syncIface["SyncService (interface)"]
        firebaseSync["FirebaseSyncService\n(push/pull, nyeste vinner)"]
        storage["DocumentStorage (expect/actual)\nlokale PDF-er"]
        dbGen["AppDatabase (SQLDelight-generert)"]
    end

    sqlite[("SQLite\nchecklist.db\n(lokal-først, offline)")]
    firestore[("Firebase Firestore\n(default) · eur3\nanonym auth + regler")]

    iosViews -- "SKIE: Flow → AsyncSequence\nsuspend → async/await" --> repo
    activity --> shells
    composeScreens --> repo
    repo --> dbGen
    seeder --> dbGen
    firebaseSync -- implementerer --> syncIface
    firebaseSync --> dbGen
    firebaseSync <--> firestore
    dbGen --> sqlite
    iosViews --> storage
    composeScreens --> storage
```

## Notater

- **Lokal-først**: alt UI leser/skriver kun mot SQLite via repositoryet. Firestore er
  et synkroniseringslag, aldri en forutsetning – appen fungerer fullt ut uten dekning.
- **UI deles ikke**: SwiftUI på iOS (hovedplattform), Compose på Android.
  All logikk, validering og datamodell deles i sharedLogic.
- **Én Android-app for både telefon og nettbrett**: `App.kt` måler tilgjengelig bredde
  med `BoxWithConstraints` og eksponerer `LocalIsCompact`. Under 600 dp brukes
  bunnavigasjon og én-kolonne layout (som iOS-appen), over 600 dp sidemeny og
  fler-kolonne. Skjermene er de samme – kun plasseringen endres.
- **SyncService-kontrakten** gjør Firebase utbyttbar med et internt Røde Kors-API
  uten endringer i app-lagene.
