# Debug-varianten (testmiljø)

Debug-bygg kjører mot Firebase-testprosjektet `redcross-ambulanse-dev`,
ikke mot produksjon. Slik unngås det at testkontroller og testavvik havner
i den ekte historikken mannskapet forholder seg til.

## Hva som må ligge her

`google-services.json` fra testprosjektet, registrert på pakkenavnet
`no.oslorodekors.ambulanse.debug` (merk `.debug` – det kommer fra
`applicationIdSuffix` i build.gradle.kts).

Filen er utelatt fra git, som produksjonsfilen.

## Slik skiller variantene seg

| | Debug | Release |
|---|---|---|
| Firebase-prosjekt | redcross-ambulanse-dev | redcross-ambulanse |
| applicationId | no.oslorodekors.ambulanse.debug | no.oslorodekors.ambulanse |
| Appnavn på enheten | RK Ambulanse TEST | RedCross-Ambulance |
| Signering | debug-nøkkel (automatisk) | redcross.jks |

Ulikt applicationId betyr at begge kan være installert samtidig på samme
nettbrett, med hver sin lokale database. Nyttig når en oppdatering skal
verifiseres før den sendes ut.

## Bygg

```
./gradlew :androidApp:installDebug     # testversjon
./gradlew :androidApp:betaRelease      # ekte versjon til mannskapet
```

Sjekk i `[Sync]`-loggen hvilket prosjekt appen faktisk snakker med hvis du
er i tvil om hvilken variant som kjører.
