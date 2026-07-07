package org.example.project.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.db.AppDatabase
import org.example.project.model.TemplateType
import org.example.project.util.randomId

/**
 * Legger inn startinnhold første gang appen kjøres (tom database).
 * MIDLERTIDIG: erstattes av Firestore-sync når Firebase kobles på.
 * Alt innhold kan redigeres i appen etterpå.
 */
class DatabaseSeeder(private val db: AppDatabase) {

    suspend fun seedIfEmpty() = withContext(Dispatchers.Default) {
        if (db.checklistTemplateQueries.countTemplates().executeAsOne() > 0L) return@withContext

        db.transaction {
            // --- Ambulanse (eksempel) ---
            db.ambulanceQueries.insertAmbulance(randomId(), "Ambulanse 1", "")

            // --- Daglig sjekkliste ---
            val daily = template("Daglig sjekk", TemplateType.DAILY)
            items(
                daily,
                "Drivstoff/lading (min. 3/4)",
                "Motorolje og væsker",
                "Dekk og lufttrykk",
                "Lys, blålys og sirene",
                "Samband (nødnettradio) – funksjonstest",
                "Kupé ren og ryddig",
                "Båre med stropper",
                "Bærestol",
                "Oksygen hovedflaske – nivå",
                "Hjertestarter – egenkontroll OK",
                "Sug – funksjonstest",
                "Blodtrykksmåler",
                "SpO2-måler",
                "Termometer",
                "Refleksvester",
                "Brannslukker",
            )

            // --- Sekker under daglig ---
            val akutt = template("Akuttsekk", TemplateType.BAG, parentId = daily)
            items(
                akutt,
                "BVM med reservoar",
                "O2-flaske – nivå",
                "Oksygenmasker (voksen/barn)",
                "Munn-svelgtuber (alle størrelser)",
                "Tourniquet",
                "Bandasjemateriell",
                "Sårrens (NaCl)",
                "Saks",
                "Hansker",
                "Lommemaske",
            )

            val oksygen = template("Oksygensekk", TemplateType.BAG, parentId = daily)
            items(
                oksygen,
                "O2-flaske – nivå og ventil",
                "Regulator",
                "Masker og slanger",
                "Reservenøkkel til flaske",
            )

            val medisin = template("Medisinveske", TemplateType.BAG, parentId = daily)
            items(
                medisin,
                "Naloxon nesespray – antall og utløpsdato",
                "Glukose/gel",
                "Medisinliste oppdatert",
                "Plombering intakt",
            )

            // --- Ukentlig ---
            val weekly = template("Ukentlig sjekk", TemplateType.WEEKLY)
            items(
                weekly,
                "Full vask av kjøretøy",
                "Kontroll av utløpsdatoer forbruksmateriell",
                "Lading av reserveutstyr/batterier",
                "Etterfylling fra stasjonslager",
                "Funksjonstest av sug",
            )

            // --- Månedlig ---
            val monthly = template("Månedlig sjekk", TemplateType.MONTHLY)
            items(
                monthly,
                "Gjennomgang beredskapsplan og tiltakskort",
                "Hjertestarter – elektroder og batteri (utløpsdato)",
                "Oksygenflasker – sertifisering og nivå",
                "Inventar kontrollert mot innholdslister",
                "Førstehjelpsutstyr – utløpsdatoer",
            )

            // --- Standardlenker (URL settes i appen) ---
            db.appLinkQueries.insertLink(randomId(), "Avviksmelding (Forms)", "", 0)
            db.appLinkQueries.insertLink(randomId(), "Registrering bruk av naloxon", "", 1)
            db.appLinkQueries.insertLink(randomId(), "Registrering utdeling/bruk av medisiner", "", 2)
        }
    }

    /** Oppretter mal og returnerer id. Må kalles inne i transaksjonen. */
    private fun template(name: String, type: TemplateType, parentId: String? = null): String {
        val id = randomId()
        db.checklistTemplateQueries.insertTemplate(id, name, type.db, parentId, 0)
        return id
    }

    /** Legger inn punkter med stigende sortOrder. Må kalles inne i transaksjonen. */
    private fun items(templateId: String, vararg titles: String) {
        titles.forEachIndexed { index, title ->
            db.checklistItemQueries.insertItem(
                randomId(), templateId, title, null, (index + 1).toLong(),
            )
        }
    }
}
