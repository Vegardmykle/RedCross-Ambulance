# Database ER Diagram

```mermaid
erDiagram
    Ambulance ||--o{ ChecklistRun : "used in"
    User ||--o{ ChecklistRun : "signed by"
    User ||--o{ ChecklistResponse : "resolved by"
    ChecklistTemplate ||--o{ ChecklistTemplate : "has bags/sections (parentId)"
    ChecklistTemplate ||--|{ ChecklistItem : "contains"
    ChecklistTemplate ||--o{ ChecklistRun : "instantiated as"
    ChecklistRun ||--|{ ChecklistResponse : "has"
    ChecklistItem ||--o{ ChecklistResponse : "answered in"

    Ambulance {
        string id PK
        string callSign
        string registrationNumber
        int updatedAt "sync"
        int deleted "soft delete"
        int synced
    }

    User {
        string id PK "mannskaps-ID"
        string name
        string role
        int updatedAt "sync"
        int deleted
        int synced
    }

    ChecklistTemplate {
        string id PK
        string name
        string type "DAILY|WEEKLY|MONTHLY|BAG"
        string parentId FK "BAG: hovedlisten den hører til"
        int sortOrder
        int version
        int updatedAt "sync"
        int deleted
        int synced
    }

    ChecklistItem {
        string id PK
        string templateId FK
        string title
        string description "plassering o.l."
        int requiresValue "1: må lese av verdi"
        string unit "f.eks. bar"
        real minValue "under => auto-avvik"
        real maxValue "over => auto-avvik"
        int sortOrder
        int updatedAt "sync"
        int deleted
        int synced
    }

    ChecklistRun {
        string id PK
        string templateId FK
        string ambulanceId FK
        string userId FK "settes ved signering"
        int createdAt
        int completedAt
        string status "IN_PROGRESS|COMPLETED|EXPIRED"
        string comment
        int updatedAt "sync"
        int synced
    }

    ChecklistResponse {
        string id PK
        string checklistRunId FK "UNIQUE(runId, itemId)"
        string itemId FK
        string result "JA|NEI|MANGELFULL|ODELAGT"
        string comment
        string reading "avlest verdi"
        int checkedAt
        int resolved "0/1"
        int resolvedAt
        string resolvedReading "ny verdi ved løsing"
        string resolvedVia "MANUAL|RECHECK|SUPERSEDED"
        string resolvedByRunId "kjøringen som lukket (angring)"
        string resolvedByUserId FK "signatur ved manuell løsing"
        int updatedAt "sync"
        int synced
    }

    AppLink {
        string id PK
        string title
        string url "forms-skjema"
        int sortOrder
        int updatedAt "sync"
        int deleted
        int synced
    }

    Document {
        string id PK
        string title
        string uri "lokal PDF-filsti"
        int sortOrder
        int updatedAt "sync"
        int deleted
        int synced
    }
```

## Notater

- **Sekker/seksjoner**: `ChecklistTemplate` med `type = BAG` og `parentId` til hovedlisten.
  Brukes både for fysiske tasker (Akuttkoffert) og bilens seksjoner (Førerkupe, Sidedør …).
  Én kjøring av hovedlisten dekker alle punkter, også i sekkene.
- **Signering**: `ChecklistRun.userId` settes ved lukking; `completeRun` krever gyldig
  mannskaps-ID og at alle punkter er besvart. Lukkede kjøringer kan ikke endres (også
  håndhevet i Firestore-reglene).
- **Utløp**: usignert kjøring fra en tidligere dag merkes `EXPIRED` (bevares i arkivet)
  og ny kjøring startes automatisk.
- **Målepunkter**: `requiresValue`/`unit`/`minValue`/`maxValue`. Avlest verdi lagres i
  `reading`; verdier utenfor grensene flagges automatisk som MANGELFULL.
- **Avvikets livssyklus**: åpent til det enten løses manuelt (`MANUAL`, med signatur og
  ev. ny verdi), er OK ved senere kontroll (`RECHECK`), eller erstattes av nytt avvik på
  samme punkt (`SUPERSEDED` = «videreført»). `resolvedByRunId` gjør angring mulig når
  svar endres i gjeldende kjøring.
- **Sync (Firestore)**: alle tabeller har `updatedAt` (sist endret), `synced`
  (0 = usynkede lokale endringer) og – for redigerbare data – `deleted` (tombstone).
  Én collection per tabell, dokument-ID = radens ID, nyeste `updatedAt` vinner ved pull.
- **Document.uri**: PDF lagret lokalt via `DocumentStorage` (offline). Synkes ikke
  (krever Firebase Storage/Blaze).
