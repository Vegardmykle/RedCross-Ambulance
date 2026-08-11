# Testdekning og risiko

Forvaltningsdokument. Viser hva som er verifisert med automatiske tester, hva som
ikke er det, og hvilken risiko som følger av hullene. Formålet er ikke å vise en
høy dekningsprosent, men å gjøre det tydelig hvor en feil faktisk får konsekvenser.

Sist oppdatert: 11. august 2026 · 33 automatiske tester, alle grønne.

## Oversikt

```mermaid
flowchart TB
    subgraph Testet["Dekket av automatiske tester (33)"]
        direction LR
        sign["Signering og fullføring\n8 tester"]
        limits["Grenseverdier på målinger\n13 tester"]
        lifecycle["Avvikslivssyklus\n12 tester"]
    end

    subgraph Delvis["Delvis dekket – indirekte"]
        crud["Maler, punkter, mannskap\n(brukes av testene, ikke testet selv)"]
    end

    subgraph Udekket["Ikke dekket"]
        direction LR
        sync["Firebase-synk\npush/pull, konflikt"]
        pdf["Lokale PDF-er\nimport og visning"]
        ui["UI: SwiftUI og Compose"]
        clock["Dagsgrense og utløp\nkrever klokkeinjeksjon"]
    end

    Testet --> risk1["Risiko: lav\nRegresjoner fanges ved bygg"]
    Delvis --> risk2["Risiko: moderat\nFeil oppdages av eksisterende tester\nsom faller sammen"]
    Udekket --> risk3["Risiko: må håndteres\nmed manuell test før utrulling"]

    style Testet fill:#E8F5E9,stroke:#2E7D32
    style Delvis fill:#FFF8E1,stroke:#B26A00
    style Udekket fill:#FFEBEE,stroke:#BA1A1A
```

## Hva som er dekket

| Område | Tester | Hva som verifiseres | Konsekvens hvis det svikter |
|---|---|---|---|
| Signering og fullføring | `CompleteRunTest` (8) | Mannskaps-ID er påkrevd; alle punkter må være besvart, også de i sekker; slettede punkter blokkerer ikke; dobbeltsignering avvises; svar låses etter signering | En sjekkliste kunne blitt godkjent uten at alt utstyr faktisk er kontrollert |
| Grenseverdier på målinger | `MeasurementLimitsTest` (13) | Verdi utenfor min/maks flagges automatisk som mangelfull selv ved JA-svar; grensene er inklusive; ugyldige tall avvises; løsing krever ny verdi innenfor grensa; gammel verdi bevares | Et oksygenapparat med for lavt trykk kunne stått registrert som «i orden» |
| Avvikslivssyklus | `DeficiencyLifecycleTest` (12) | Automatisk lukking ved JA neste vakt (RECHECK); videreføring ved nytt avvik (SUPERSEDED) med sporing tilbake til første melding; angring ved endret svar i samme kontroll; avvik lekker ikke mellom ambulanser; manuell løsing overskrives ikke | Avvik kunne forsvunnet fra oversikten uten å være rettet, eller blitt dobbeltført |

Kjøres med `./gradlew :sharedLogic:testAndroidHostTest`. Hver test får sin egen
SQLite-database i minnet – ingen emulator, ingen nettverk, ingen Firebase.
Full kjøring tar under ett minutt.

## Hva som ikke er dekket

| Område | Hvorfor ikke | Risiko | Kompenserende tiltak |
|---|---|---|---|
| Firebase-synk (`FirebaseSyncService`) | Krever nettverk og et ekte Firestore-prosjekt | **Moderat.** Appen er lokal-først, så en synkfeil stopper ikke arbeidet i bilen – men data kan bli stående usynkronisert mellom enheter | Testes manuelt med to enheter før utrulling: meld avvik på enhet A, trykk synk på enhet B, se at det kommer fram |
| Dagsgrense og utløp (`startOrResumeRun`) | `currentTimeMillis()`/`startOfTodayMillis()` kalles direkte og kan ikke styres fra en test | **Moderat.** En påbegynt liste kan i verste fall gjenbrukes over døgnskillet i stedet for å starte på nytt | Kan testes automatisk hvis klokka injiseres – anbefalt neste steg |
| Lokale PDF-er (`DocumentStorage`) | Plattformspesifikk filhåndtering (iOS og Android hver for seg) | **Lav.** Dokumentene er statiske; en feil merkes umiddelbart ved at PDF-en ikke åpner | Manuell sjekk ved import av nye instrukser |
| Brukergrensesnitt (SwiftUI og Compose) | Endres ofte; enhetstester her gir lav avkastning | **Lav til moderat.** Logikkfeil fanges av testene over, men feil i navigasjon eller knappetilstand fanges ikke | Én gjennomgang av hovedflyten på hver plattform før utrulling: start liste → svar nei → signer → se avviket i arkivet |
| Adaptiv layout (telefon/nettbrett) | Rent visuelt | **Lav.** | Visuell sjekk på iPhone, iPad, Android-telefon og Android-nettbrett |
| Seeding (`DatabaseSeeder`) | – | **Lav i dag.** Faste ID-er gjør gjentatt seeding trygg, men det er ikke verifisert automatisk | Bør legges til; enkel test (kjør to ganger, sjekk antall rader) |
| Hjelpefunksjoner for tall og URL | Ligger i `sharedUI` og er dermed Android-bundet | **Lav.** | Bør flyttes til `sharedLogic/util` så iOS og Android bruker samme regler og testes én gang |
| CRUD på maler, punkter, mannskap, kjøretøy, lenker | Brukes indirekte av alle testene over | **Lav.** En feil her ville fått testene over til å falle | – |

## Anbefalt rekkefølge for videre arbeid

1. **Klokkeinjeksjon** slik at dagsgrensen kan testes. Dette er det største gjenværende
   hullet i forretningslogikken.
2. **Flytt hjelpefunksjonene** (`normalizeNumber`, `filterNumeric`, `normalizeUrl`) til
   `sharedLogic/util`. Gir felles regler på begge plattformer og testes én gang.
3. **Test av seeding** – to kjøringer skal ikke gi duplikater.
4. **Én gjennomgående UI-test per plattform** over hovedflyten. Fanger mer enn mange
   små UI-enhetstester.

## Vedlikehold av dette dokumentet

Oppdateres når det legges til tester eller ny funksjonalitet. Nye funksjoner som
påvirker pasientsikkerhet – validering, signering, avvikshåndtering – skal ha
tester før de tas i bruk. Rent visuelle endringer krever det ikke.

Se også: [`deviation-lifecycle.md`](deviation-lifecycle.md) for tilstandsmaskinen
testene verifiserer, og [`architecture.md`](architecture.md) for lagdelingen.
