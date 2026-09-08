package org.example.project.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import org.example.project.db.AppDatabase

/**
 * Driveren kjører migrasjoner automatisk: den sammenligner versjonen i
 * databasefila på enheten med [AppDatabase.Schema].version og kjører
 * migrasjonsfilene i mellom. Se
 * `sharedLogic/src/commonMain/data/sqldelight/migrations/README.md`.
 *
 * Feiler en migrasjon, kastes unntaket videre i stedet for å slette
 * databasen – lokale, usynkede kontroller er dokumentasjon og skal ikke
 * forsvinne stille.
 */
fun createDriver(): SqlDriver {
    return NativeSqliteDriver(
        AppDatabase.Schema,
        "checklist.db"
    )
}
