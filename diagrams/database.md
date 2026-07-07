# Database ER Diagram

```mermaid
erDiagram
    Ambulance ||--o{ ChecklistRun : "used in"
    User ||--o{ ChecklistRun : "signed by"
    ChecklistTemplate ||--o{ ChecklistTemplate : "has bags (parentId)"
    ChecklistTemplate ||--|{ ChecklistItem : "contains"
    ChecklistTemplate ||--o{ ChecklistRun : "instantiated as"
    ChecklistRun ||--|{ ChecklistResponse : "has"
    ChecklistItem ||--o{ ChecklistResponse : "answered in"

    Ambulance {
        string id PK
        string callSign
        string registrationNumber
    }

    User {
        string id PK "mannskaps-ID"
        string name
        string role
    }

    ChecklistTemplate {
        string id PK
        string name
        string type "DAILY|WEEKLY|MONTHLY|BAG"
        string parentId FK "BAG: hovedlisten sekken hører til"
        int sortOrder
        int version
    }

    ChecklistItem {
        string id PK
        string templateId FK
        string title
        string description
        int sortOrder
    }

    ChecklistRun {
        string id PK
        string templateId FK
        string ambulanceId FK
        string userId FK "mannskaps-ID (User.id)"
        int createdAt
        int completedAt
        string status "IN_PROGRESS|COMPLETED"
        string comment
        int synced "0/1 – for senere Firebase-sync"
    }

    ChecklistResponse {
        string id PK
        string checklistRunId FK
        string itemId FK "UNIQUE(runId, itemId)"
        string result "JA|NEI|MANGELFULL|ODELAGT"
        string comment
        int checkedAt
        int resolved "0/1 – avvik fulgt opp"
    }

    AppLink {
        string id PK
        string title
        string url "avviksmelding, naloxon, medisin (forms)"
        int sortOrder
    }

    Document {
        string id PK
        string title
        string uri "lokal filsti (PDF lagret i appen)"
        int sortOrder
    }
```

## Notater

- **Sekker/tasker**: egne `ChecklistTemplate` med `type = BAG` og `parentId` til hovedlisten.
  En kjøring av daglig liste dekker også sekkenes punkter
- **Mangler**: `getOpenDeficiencies` henter alle responses med resultat NEI/MANGELFULL/ODELAGT
  som ikke er `resolved` – vises på egen oversiktsside.
- **Document.uri**: peker på PDF lagret lokalt via `DocumentStorage`
- **User**: opprettes med mannskaps-ID som id (`addUser`), brukes ved signering.
