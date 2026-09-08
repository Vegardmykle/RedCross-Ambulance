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
    Sync->>DB: hent rader med synced = 0
    Sync->>FS: push (set per dokument, ID = rad-ID)
    Sync->>DB: marker synced = 1
    Sync->>FS: pull alle collections
    FS-->>Sync: dokumenter
    Sync->>DB: applyRemote hvis remote.updatedAt > lokal.updatedAt
    Note over Sync,DB: Seed kjøres KUN hvis databasen<br/>fortsatt er tom etter pull<br/>(faste ID-er hindrer duplikater)

    Note over UI,FS: Under vakt (med eller uten dekning)
    UI->>Repo: svar / signering / løs avvik
    Repo->>DB: skriv + updatedAt = nå, synced = 0
    DB-->>UI: Flow oppdaterer skjermen umiddelbart

    Note over UI,FS: Etter signering / løst avvik
    UI->>Sync: syncAll() (i bakgrunnen)
    alt Har dekning
        Sync->>FS: push + pull
        Sync->>DB: synced = 1
    else Uten dekning
        Sync--xFS: feiler stille (status = Error)
        Note over DB: Radene beholder synced = 0<br/>og sendes ved neste anledning
    end
```

## Notater

- **Appen venter aldri på nettet**: alle skriveoperasjoner går til SQLite og UI-et
  oppdateres via Flow. Sync er en bakgrunnsjobb ved appstart, etter signering og
  etter løst avvik.
- **Konflikthåndtering**: nyeste `updatedAt` vinner per dokument (last-write-wins).
  Godt nok fordi to enheter sjelden redigerer samme rad, og kjøringer/svar er
  enhets-spesifikke frem til signering.
- **Slettinger** synkes som tombstones (`deleted = 1`), aldri som fysisk sletting –
  nødvendig for at en fremtidig intern Røde Kors-backend skal kunne overta
  (SyncService-kontrakten).
- **Sikkerhet**: Firestore-reglene krever innlogget enhet, nekter endring av signerte
  kjøringer og all sletting.
