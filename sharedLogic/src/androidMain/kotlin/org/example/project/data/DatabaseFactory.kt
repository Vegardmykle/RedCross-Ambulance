package org.example.project.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
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
fun createDriver(context: Context): SqlDriver {
    return AndroidSqliteDriver(
        AppDatabase.Schema,
        context,
        "checklist.db"
    )
}
