# RedCross-Ambulance

Sjekkliste- og avviksapp for ambulansegruppa i Oslo Røde Kors. Mannskapet
gjennomfører daglige, ukentlige og månedlige utstyrskontroller på iPhone/iPad
eller Android, signerer med mannskaps-ID, og avvik følges opp til de er rettet.
Appen fungerer fullt ut uten nettdekning – synkronisering mellom enheter skjer
i bakgrunnen når det er nett.

## Hva appen gjør

- **Sjekklister** med ja / nei / mangelfull / ødelagt per punkt, organisert i
  hovedliste og sekker/tasker (akuttkoffert, oksygensekk, branntaske osv.)
- **Målepunkter** med grenseverdier (f.eks. oksygentrykk 180–250 bar) – verdier
  utenfor grensa flagges automatisk som avvik, uansett hva mannskapet svarte
- **Signering** med mannskaps-ID kreves for å lukke en liste, og alle punkter
  må være besvart først
- **Avviksoppfølging**: åpne mangler vises på tvers av vakter til de løses –
  enten manuelt med signatur og ny måleverdi, eller automatisk når punktet
  meldes i orden ved neste kontroll. Videreførte avvik spores tilbake til
  første melding
- **Redigerbare lister**: mannskapet kan endre punkter, grenseverdier, sekker
  og rekkefølge rett i appen; endringene deles til alle enheter
- **Ressurser**: lokale PDF-er (interne instrukser, beredskapsplan med
  tiltakskort for ekom-bortfall) og hurtiglenker til avviksskjema,
  naloksonregistrering og medikamentregistrering
- **Arkiv** over gjennomførte kontroller med hvem som signerte og hvilke
  avvik som ble meldt og løst

## Arkitektur i korte trekk

Kotlin Multiplatform med **native UI på begge plattformer** – SwiftUI på
iOS/iPadOS (hovedplattform), Jetpack Compose på Android. Kun logikk og data
deles. Se [`diagrams/architecture.md`](diagrams/architecture.md).

```
RedCross-Ambulance/
├── sharedLogic/     Delt kjerne (Kotlin Multiplatform)
│   ├── ChecklistRepository – all forretningslogikk og validering
│   ├── SQLDelight-skjema → SQLite (lokal-først, alltid kilden til sannhet)
│   ├── FirebaseSyncService – bakgrunnssynk mot Firestore, «nyeste vinner»
│   └── DatabaseSeeder – ekte sjekklistedata med faste ID-er
├── iosApp/          SwiftUI-app (iPhone og iPad)
├── sharedUI/        Compose-skjermer (kun Android)
│   └── App.kt velger layout: bunnfaner < 600 dp, sidemeny ≥ 600 dp
├── androidApp/      Android-skall (én APK for telefon og nettbrett)
└── diagrams/        Arkitektur, datamodell, avvikslivssyklus, synk, testdekning
```

Prinsipper som styrer koden:

- **Lokal-først**: UI leser og skriver kun mot SQLite. Firestore er et
  synkroniseringslag, aldri en forutsetning. Ingen dekning = ingen forskjell.
- **Utbyttbar synk**: `SyncService`-grensesnittet gjør at Firebase kan byttes
  ut med et internt Røde Kors-API uten endringer i appene.
- **Myk sletting**: rader merkes `deleted` og synkes som tombstones, slik at
  sletting når alle enheter.
- **Kotlin → Swift** via SKIE: Flow blir AsyncSequence, suspend blir
  async/await. Suspend-funksjoner som kan feile er merket `@Throws` – uten
  dette krasjer iOS-appen i stedet for å vise feilmelding.

## Komme i gang

Krever JDK 17, Android Studio (nyere versjon med AGP 9) og Xcode.

**Android**

```
./gradlew :androidApp:assembleDebug
```

**iOS**: åpne `iosApp/` i Xcode og kjør. Firebase-avhengighetene
(FirebaseCore, FirebaseAuth, FirebaseFirestore) hentes via Swift Package
Manager ved første bygg.

**Firebase**: hver utvikler/utrulling trenger `google-services.json`
(androidApp/) og `GoogleService-Info.plist` (iosApp/) fra
Firebase-konsollen. Begge er bevisst utelatt fra git. Firestore må være
Standard edition med database-ID `(default)`, region eur3, og anonym
autentisering påslått.

**Første kjøring**: appen synker først, og seeder bare hvis databasen
fortsatt er tom – slik unngås duplikater når flere enheter settes opp.
Start derfor én enhet av gangen ved førstegangsoppsett.

## Distribusjon til testere (Android)

Beta rulles ut via Firebase App Distribution. Testerne ligger i gruppa
`ambulansegruppa` i Firebase-konsollen og får varsel om nye versjoner
automatisk.

```
firebase login          # én gang
./gradlew :androidApp:betaRelease
```

Krever at signeringsnøkkelen er satt opp i `local.properties`
(`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD`). Fila er utelatt fra git. Skriv hva som er nytt i
`release-notes.txt` før du kjører — teksten vises for testerne.

**Nøkkelen må ikke mistes.** Uten den kan appen aldri oppdateres; testerne
må avinstallere og installere på nytt, og usynkede data på enheten går tapt.
Ta backup av `.jks`-fila og passordene, og del dem med en ansvarlig i
ambulansegruppa.

## Tester

```
./gradlew :sharedLogic:testAndroidHostTest
```

33 tester dekker det som har konsekvenser hvis det svikter: signering og
fullføring, grenseverdier på målinger og avvikslivssyklusen. Hver test kjører
mot en egen SQLite-database i minnet – ingen emulator eller nettverk.
[`diagrams/test-coverage.md`](diagrams/test-coverage.md) viser hva som er
dekket, hva som ikke er det, og hvilken manuell testing som kompenserer.

## Dokumentasjon

| Dokument | Innhold |
|---|---|
| [`diagrams/architecture.md`](diagrams/architecture.md) | Moduler, lagdeling og dataflyt |
| [`diagrams/database.md`](diagrams/database.md) | ER-diagram for SQLite-skjemaet |
| [`diagrams/deviation-lifecycle.md`](diagrams/deviation-lifecycle.md) | Tilstandsmaskinen for avvik: åpent → løst/videreført |
| [`diagrams/sync-flow.md`](diagrams/sync-flow.md) | Sekvensdiagram for push/pull mot Firestore |
| [`diagrams/test-coverage.md`](diagrams/test-coverage.md) | Testdekning, risiko og anbefalt videre arbeid |

## Kjente begrensninger

- Synk krever manuell utløsning (synk-knapp / pull-to-refresh) eller
  app-start; ingen kontinuerlig lytting mot Firestore ennå
- Dagsgrensen i `startOrResumeRun` er ikke automatisk testet (krever
  klokkeinjeksjon – øverst på lista i test-coverage.md)
