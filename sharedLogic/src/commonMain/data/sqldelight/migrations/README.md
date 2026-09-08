# Databasemigrasjoner

Appen er i drift hos mannskapet, og enhetene har lokale data som ikke alltid
er synkronisert. En skjemaendring uten migrasjon vil derfor enten krasje
appen eller slette usynkede kontroller.

## Slik gjør du en skjemaendring

1. **Endre `.sq`-filene** i `../database/` til hvordan skjemaet skal se ut
   etter endringen. Disse beskriver alltid *nyeste* versjon.

2. **Legg til en migrasjonsfil** her, navngitt med versjonen den migrerer
   *fra*. Den første heter `1.sqm` og tar databasen fra versjon 1 til 2:

   ```sql
   -- 1.sqm
   ALTER TABLE ChecklistItem ADD COLUMN phase TEXT NOT NULL DEFAULT 'BEFORE';
   ```

3. **Generer skjemafilen** som verifiseringen sammenligner mot:

   ```
   ./gradlew :sharedLogic:generateAppDatabaseSchema
   ```

   Det legger en `<versjon>.db` i `../databases/`. Den skal commites.

4. **Bygg.** `verifyMigrations` feiler hvis migrasjonene ikke ender opp med
   nøyaktig samme skjema som `.sq`-filene beskriver.

## Regler som er verdt å kjenne

**Versjonsnummeret er antall migrasjonsfiler + 1.** Uten `.sqm`-filer er
databasen versjon 1. Legger du til `1.sqm`, blir den versjon 2, og
driverne kjører migrasjonen automatisk på enheter som står på versjon 1.

**SQLite kan lite.** `ALTER TABLE ... ADD COLUMN` går fint. Å endre eller
fjerne en kolonne krever at du lager en ny tabell, kopierer data over,
sletter den gamle og døper om den nye. Skriv det ut i migrasjonsfila.

**Nye kolonner må ha `DEFAULT` eller tillate NULL.** Eksisterende rader har
ingen verdi å fylle inn, og migrasjonen feiler uten.

**Migrasjoner som er sendt ut skal aldri endres.** Er `1.sqm` kjørt på en
enhet i en bil, er den historie. Trenger du å rette noe, lag `2.sqm`.

**Husk synkroniseringen.** En ny kolonne som skal deles mellom enheter må
også legges til i `SyncDtos.kt` og i `applyRemote*`-spørringene, ellers
finnes den bare lokalt.
