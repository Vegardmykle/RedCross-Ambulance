# Avvikets livssyklus

Et avvik er et svar (`ChecklistResponse`) med resultat NEI, MANGELFULL eller ØDELAGT.
Målepunkter utenfor grenseverdiene flagges automatisk som MANGELFULL.

```mermaid
stateDiagram-v2
    [*] --> Apent : Svar ≠ JA registreres\n(ev. auto pga. verdi utenfor min/maks)

    Apent : Åpent avvik
    Apent : vises på Mangler-siden
    Apent : varsel på punktet i neste kontroll

    Apent --> LostManuelt : «Marker som løst»\nkrever mannskaps-ID\n+ ny verdi for målepunkter\n(innenfor grensene)
    Apent --> LostRecheck : Punktet svares JA\ni senere kontroll\n(samme ambulanse)
    Apent --> Videreført : Nytt avvik på samme punkt\ni senere kontroll

    LostManuelt : Løst manuelt (MANUAL)
    LostManuelt : resolvedAt + resolvedByUserId\n+ ev. resolvedReading

    LostRecheck : OK ved senere kontroll (RECHECK)
    LostRecheck : resolvedAt + resolvedByRunId

    Videreført : Videreført (SUPERSEDED)
    Videreført : det nye avviket arver «først meldt»\n(kjeden følges via resolvedByRunId)

    LostRecheck --> Apent : Angring – svaret i samme\nkjøring endres tilbake til avvik
    Videreført --> Apent : Angring – det nye svaret\nendres til JA eller fjernes

    LostManuelt --> [*]
    LostRecheck --> [*]
    Videreført --> [*] : følges videre av det nye avviket
```

## Notater

- **Kun åpne avvik** vises på Mangler-siden. Videreførte representeres av det nyeste
  avviket i kjeden, som viser «Videreført · først meldt {dato} av {navn}».
- **Arkivet** viser alle tilstander: åpne med resultat-badge, løste med grønn «Løst»
  (og hvordan/hvem/ny verdi), videreførte med oransje «Videreført».
- **Angring** er trygg: alle automatiske lukkinger husker hvilken kjøring som forårsaket
  dem (`resolvedByRunId`) og rulles tilbake hvis svaret endres i samme kjøring.
- **Historikk-telling**: «2 åpne avvik · 1 løst · 1 videreført» – videreført regnes
  aldri som løst.
