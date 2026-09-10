# Synkroniseringsflyt (lokal-først)

> **Rekkefølge: pull før push.** Push skriver hele dokumentet uten å
> sammenligne med det som allerede ligger i skyen. En enhet som har vært
> offline ville derfor kunne overskrive nyere endringer fra de andre bilene
> med sin egen utdaterte versjon. Ved å hente først blir lokale rader som er
> eldre enn skyen erstattet, og de er dermed ikke lenger usynkede når pushen
> kjører. Sikkerhetsreglene håndhever i tillegg at `updatedAt` aldri kan gå
> bakover – det fanger kappløpet der to enheter pusher samtidig.

```mermaid
sequenceDiagram
    participant UI as App-UI
    participant Repo as ChecklistRepository
    participant DB as SQLite (lokal)
    participant Sync as FirebaseSyncService
    participant FS as Firestore (default)

    Note over UI,FS: Appstart
    Sync->>FS: signInAnonymously (hvis nødvendig)
    Sync->>FS: pull – maler, punkter, mannskap, kjøretøy, lenker i sin helhet
    Sync->>FS: pull – kjøringer og svar endret etter forrige henting
    FS-->>Sync: dokumenter
    Sync->>DB: applyRemote hvis remote.updatedAt > lokal.updatedAt
    Sync->>DB: hent rader med synced = 0
    Sync->>FS: push (set per dokument, ID = rad-ID)
    Sync->>DB: marker synced = 1
    Sync->>DB: bokfør hentetidspunktet i SyncState
    Note over Sync,DB: Seed kjøres KUN hvis databasen<br/>fortsatt er tom etter pull<br/>(faste ID-er hindrer duplikater)

    Note over UI,FS: Under vakt (med eller uten dekning)
    UI->>Repo: svar på punkt
    Repo->>DB: skriv + updatedAt = nå, synced = 0
    DB-->>UI: Flow oppdaterer skjermen umiddelbart
    Note over Repo,Sync: Enkeltsvar utløser ikke synk –<br/>de sendes samlet ved signering

    Note over UI,FS: Ved signering, løst avvik eller endring i lista
    Repo->>Sync: requestSync() (i bakgrunnen)
    alt Har dekning
        Sync->>FS: pull + push
        Sync->>DB: synced = 1
    else Uten dekning
        Sync--xFS: feiler stille (status = Offline)
        Note over DB: Radene beholder synced = 0<br/>og sendes ved neste anledning
    end
```

## Notater

- **Appen venter aldri på nettet**: alle skriveoperasjoner går til SQLite og UI-et
  oppdateres via Flow. Synk er en bakgrunnsjobb ved appstart og når noe delbart
  endres.
- **Repositoryet utløser synken**, ikke skjermene. Utløseren lå tidligere som en
  callback tredd gjennom UI-treet, og to skjermer på Android fikk den aldri.
  Svar på enkeltpunkter er bevisst unntatt: mannskapet krysser av hundrevis av
  punkter i en kontroll, og de sendes samlet ved signering.
- **Kjøringer og svar hentes inkrementelt.** De er de eneste kolleksjonene som
  vokser monotont – én daglig kontroll er rundt 150 svar, altså over 50 000
  dokumenter i året per bil. Hentingen spør fra forrige hentetidspunkt minus et
  døgn. Overlappen er nødvendig fordi `updatedAt` settes fra klokka på enheten
  som skrev raden: uten den ville en rad skrevet av en enhet med litt etterslepende
  klokke aldri blitt hentet. Resten av kolleksjonene er små og hentes i sin helhet.
- **Konflikthåndtering**: nyeste `updatedAt` vinner per dokument (last-write-wins).
  Godt nok fordi to enheter sjelden redigerer samme rad, og kjøringer/svar er
  enhets-spesifikke frem til signering.
- **Slettinger** synkes som tombstones (`deleted = 1`), aldri som fysisk sletting –
  nødvendig for at en fremtidig intern Røde Kors-backend skal kunne overta.
- **Sikkerhet**: Firestore-reglene krever innlogget enhet, nekter endring av signerte
  kjøringer og all sletting.
