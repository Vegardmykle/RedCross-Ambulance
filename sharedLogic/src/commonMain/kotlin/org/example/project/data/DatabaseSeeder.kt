package org.example.project.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.db.AppDatabase
import org.example.project.model.TemplateType
import org.example.project.util.currentTimeMillis
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
            db.ambulanceQueries.insertAmbulance(randomId(), "Ambulanse 1", "", currentTimeMillis())

            // --- Testbruker (mannskaps-ID for signering) ---
            db.userQueries.insertUser("12345", "Test Bruker", "Mannskap", currentTimeMillis())

            // --- Daglig sjekkliste ---
            val daily = template("Daglig sjekk", TemplateType.DAILY)
            items(
                daily,
                i("Drivstoff/lading (min. 3/4)"),
                i("Motorolje og væsker"),
                i("Dekk og lufttrykk"),
                i("Lys, blålys og sirene"),
                i("Samband (nødnettradio) – funksjonstest"),
                i("Kupé ren og ryddig"),
                i("Båre med stropper"),
                i("Bærestol"),
                v("Oksygen hovedflaske – nivå", "bar", min = 150.0, max = 300.0),
                i("Hjertestarter – egenkontroll OK"),
                i("Sug – funksjonstest"),
                i("Blodtrykksmåler"),
                i("SpO2-måler"),
                i("Termometer"),
                i("Refleksvester"),
                i("Brannslukker"),
            )

            // --- Sekker under daglig ---
            val akutt = template("Akuttsekk", TemplateType.BAG, parentId = daily)
            items(
                akutt,
                i("BVM med reservoar"),
                v("O2-flaske – nivå", "bar", min = 150.0, max = 300.0),
                i("Oksygenmasker (voksen/barn)"),
                i("Munn-svelgtuber (alle størrelser)"),
                i("Tourniquet"),
                i("Bandasjemateriell"),
                i("Sårrens (NaCl)"),
                i("Saks"),
                i("Hansker"),
                i("Lommemaske"),
            )

            val oksygen = template("Oksygensekk", TemplateType.BAG, parentId = daily)
            items(
                oksygen,
                v("O2-flaske – nivå og ventil", "bar", min = 150.0, max = 300.0),
                i("Regulator"),
                i("Masker og slanger"),
                i("Reservenøkkel til flaske"),
            )

            val medisin = template("Medisinveske", TemplateType.BAG, parentId = daily)
            items(
                medisin,
                i("Naloxon nesespray – antall og utløpsdato"),
                i("Glukose/gel"),
                i("Medisinliste oppdatert"),
                i("Plombering intakt"),
            )

            // --- Ukentlig ---
            val weekly = template("Ukentlig sjekk", TemplateType.WEEKLY)
            items(
                weekly,
                i("Full vask av kjøretøy"),
                i("Kontroll av utløpsdatoer forbruksmateriell"),
                i("Lading av reserveutstyr/batterier"),
                i("Etterfylling fra stasjonslager"),
                i("Funksjonstest av sug"),
            )

            // --- Månedlig ---
            val monthly = template("Månedlig sjekk", TemplateType.MONTHLY)
            items(
                monthly,
                i("Gjennomgang beredskapsplan og tiltakskort"),
                i("Hjertestarter – elektroder og batteri (utløpsdato)"),
                v("Oksygenflasker – sertifisering og nivå", "bar", min = 150.0, max = 300.0),
                i("Inventar kontrollert mot innholdslister"),
                i("Førstehjelpsutstyr – utløpsdatoer"),
            )

            // --- Standardlenker (URL settes i appen) ---
            db.appLinkQueries.insertLink(randomId(), "Avviksmelding (Forms)", "", 0, currentTimeMillis())
            db.appLinkQueries.insertLink(randomId(), "Registrering bruk av naloxon", "", 1, currentTimeMillis())
            db.appLinkQueries.insertLink(randomId(), "Registrering utdeling/bruk av medisiner", "", 2, currentTimeMillis())
        }
    }

    /** Oppretter mal og returnerer id. Må kalles inne i transaksjonen. */
    private fun template(name: String, type: TemplateType, parentId: String? = null): String {
        val id = randomId()
        db.checklistTemplateQueries.insertTemplate(id, name, type.db, parentId, 0, currentTimeMillis())
        return id
    }

    /** Legger inn punkter med stigende sortOrder. Må kalles inne i transaksjonen. */
    private fun items(templateId: String, vararg specs: ItemSpec) {
        specs.forEachIndexed { index, spec ->
            db.checklistItemQueries.insertItem(
                randomId(), templateId, spec.title, null,
                if (spec.unit != null) 1L else 0L, spec.unit,
                spec.min, spec.max,
                (index + 1).toLong(),
                currentTimeMillis(),
            )
        }
    }

    private class ItemSpec(
        val title: String,
        val unit: String?,
        val min: Double? = null,
        val max: Double? = null,
    )

    /** Vanlig ja/nei-punkt. */
    private fun i(title: String) = ItemSpec(title, null)

    /** Punkt som krever avlest verdi. Verdier utenfor [min, max] flagges som avvik. */
    private fun v(title: String, unit: String, min: Double? = null, max: Double? = null) =
        ItemSpec(title, unit, min, max)
}
