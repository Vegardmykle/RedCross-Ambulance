package org.example.project.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.project.db.AppDatabase
import org.example.project.model.TemplateType
import org.example.project.util.currentTimeMillis

/**
 * Legger inn sjekklistene første gang appen kjøres (tom database).
 * Innholdet er Oslo Røde Kors' reelle lister (beredskapsambulanse, før vakt).
 * Faste ID-er overalt: to enheter som seeder samtidig lager identiske
 * dokumenter i Firestore i stedet for duplikater.
 * Alt kan redigeres i appen etterpå.
 */
class DatabaseSeeder(private val db: AppDatabase) {

    @Throws(Exception::class, kotlin.coroutines.cancellation.CancellationException::class)
    suspend fun seedIfEmpty() = withContext(Dispatchers.Default) {
        if (db.checklistTemplateQueries.countTemplates().executeAsOne() > 0L) return@withContext

        db.transaction {
            db.ambulanceQueries.insertAmbulance("seed-ambulance-1", "Ambulanse 1", "", currentTimeMillis())
            db.userQueries.insertUser("12345", "Test Bruker", "Mannskap", currentTimeMillis())

            // ============ DAGLIG: Sjekkliste før vakt ============
            val daily = template("seed-daily", "Sjekkliste før vakt", TemplateType.DAILY)
            items(
                daily,
                i("Bilen er supplert med nytt utstyr etter vakt"),
                i("Oksygen skrudd av og taket er tømt", "Husk sjekk at ventil til stasjonært sug er stengt"),
                i("Vask av bil innvendig"),
                i("Vask av bil utvendig"),
                i("Notert km i Car admin"),
            )

            val forerkupe = template("seed-forerkupe", "Førerkupe", TemplateType.BAG, daily)
            items(
                forerkupe,
                i("Pasientjournaler"),
                i("Drivstoffkort"),
                i("Hansker div størrelser"),
                i("Vernebriller + munnbind + håndsprit"),
                i("Vakttelefon"),
                i("Hodelykter"),
                i("Plivotaske x2"),
                i("Adgangskort til Bærum og OUS", "Hanskerom"),
                i("Bomnøkler", "Hanskerom"),
            )

            val bilteknisk = template("seed-bilteknisk", "Bilteknisk", TemplateType.BAG, daily)
            items(
                bilteknisk,
                i("Dekk"),
                i("Spylevæske"),
                i("Lys inni kupe"),
                i("Blålyskontroll og kjørelys"),
            )

            val sykekupe = template("seed-sykekupe", "Sykekupe", TemplateType.BAG, daily)
            items(
                sykekupe,
                i("Ambulansevester"),
                i("Fødesett", "Under sete"),
                i("Smittekoffert", "Under sete"),
                i("Ringer i varmeveske", "Under sete"),
                i("Akuttsekk"),
                i("Hjelmer 3 stk", "Under sete"),
                i("Vakuum madrass", "Under sete"),
                i("Brodder (vinter)", "Gulv under båre"),
                i("5 stk O2 grimer"),
                i("O2 maske m/res 3x voksen og 2x barn"),
                i("Manuelt BT-apparat og stetoskop"),
                i("Øretemp. med hetter"),
                i("Komplett blodsukker-sett, tupfere, Klorhexidin, Glucogel 1 stk"),
                i("Hansker av alle størrelser"),
                i("PVK (blå, grønn, rosa, grå) + 3 veiskraner"),
                i("Sprøyter (0,1–1 ml + 10 ml), stasebånd"),
                i("Zic-zac", "Under behandlersete"),
                i("Cellestoff", "Under behandlersete"),
                i("Pasientvann + kopper", "Under behandlersete"),
                i("Urinposer", "Under behandlersete"),
                i("Nakkekrage, spider, scoopbåre, sam sling (bekkenmobilisering)"),
                i("Div. forbindingsmateriell"),
                i("Isposer, ekstra pads, pasientposer", "Luke under corepuls"),
                i("Infusjonsett"),
                i("Nalokson"),
                i("Ekstra elektroder"),
                i("Forstøvermaske"),
                i("2 stk Maske u/res"),
                i("Vent.bag voksen m/masker (orange, grønn, gul) + 1 stk pocketmask"),
                i("Svelgtuber: hvit, grønn, oransje, rød"),
                i("Sug stasjonært + sugekateter (grønne, oransje)"),
            )

            val hylleskap = template("seed-hylleskap", "Hylleskap bakdør", TemplateType.BAG, daily)
            items(
                hylleskap,
                i("Spjelkesett inkl pappspjelker"),
                i("Bæresegl"),
                i("Bandasje-, barne- og branntasker"),
                i("Tepper og putetrekk"),
                i("Hypotermipose"),
                i("Bærestol"),
                i("Overflate desinfeksjon + oxywipes"),
                i("Smittefrakker, guleposer og strips"),
                i("Sjekk av båre"),
                i("Morspose"),
                i("Hurtigkjetting (vinter)", "Nederste hylleskap"),
            )

            val sidedor = template("seed-sidedor", "Sidedør", TemplateType.BAG, daily)
            items(
                sidedor,
                v("O2-flaske i sekk – nivå", "bar"),
                i("Triagesett"),
                i("Traction splint"),
                i("Redningstau x2"),
                i("Redningsvest"),
                v("O2-flaske fastmontert – antall liter", "liter"),
                i("Brannsluknings­apparat"),
                i("Eksternt sug i lader"),
                i("Spade (vinter)"),
            )

            val multimonitor = template("seed-multimonitor", "Multimonitor", TemplateType.BAG, daily)
            items(
                multimonitor,
                i("Corepuls m/2 stk pads + ekstra pads, utskriftsrull og elektroder"),
                i("Høvel, saks"),
                i("Utført sjekk av Corepuls + SpO2 og BT"),
            )

            // ============ Sekker og kofferter ============
            val oksygensekk = template("seed-oksygensekk", "Oksygensekk", TemplateType.BAG, daily)
            items(
                oksygensekk,
                i("1 stk Oksygenkolbe", "Hovedrom. NB: Husk sjekk av innhold og utstyr etter stans-oppdrag!"),
                i("1 stk Oksygenslange (ekstra)", "Hovedrom"),
                i("2 stk maske m/res voksen", "Hovedrom"),
                i("1 stk nebulizor", "Hovedrom"),
                i("5 stk nesegrime", "Hovedrom"),
                i("1 stk pocketmask voksen", "Hovedrom"),
                i("2 stk Zic zac", "Hovedrom"),
                i("2 stk saks", "Hovedrom"),
                i("1 stk Magill tang voksen", "Hovedrom"),
                i("1 stk stetoskop", "Hovedrom"),
                i("1 stk Luftveishåndtering barn-taske", "Hovedrom – masker/bag, oksygenslange, svelgtuber, pocketmask"),
                i("1 stk sprøyte 5 ml", "Hovedrom"),
                i("5 stk svelgtuber (rød, oransje, grønn, hvit, grå)", "Hovedrom"),
                i("1 stk laryngoskop med forskjellige blader", "Sidelommer små"),
                i("2 stk maske m/res barn", "Sidelommer små"),
                i("1 stk bag m/res og oksygenslange", "Ventilasjon – sidelomme stor"),
                i("3 stk masker (orange, grønn, gul)", "Ventilasjon – sidelomme stor"),
                i("I-Gel (4 størrelser)", "Luftveishåndtering – sidelomme stor"),
            )

            val akuttkoffert = template("seed-akuttkoffert", "Akuttkoffert", TemplateType.BAG, daily)
            items(
                akuttkoffert,
                i("2 stk Idealbind"),
                i("2 stk kompresjonsbandasje"),
                i("1 stk Klinifix"),
                i("10 stk kompress 10x10"),
                i("2 stk kompress 10x20"),
                i("1 stk Co-band + bandasje"),
                i("2 stk trekanttørkle"),
                i("2 stk tape (1 blå og 1 silketape)"),
                i("2 stk store + 1 stk liten Burnshield"),
                i("5 stk Melolin"),
                i("1 stk pakke plaster"),
                i("1 stk sprøyeboks"),
                i("1 stk Antibac"),
                i("2 stk ispose"),
                i("1 stk blodsukkermåler"),
                i("1 stk Klorhexidin"),
                i("5 stk tupfer"),
                i("1 stk stasebånd"),
                i("5 stk opptreksnåler (rosa 50 mm og gul 40 mm)"),
                i("Sprøyter", "3 stk 1 ml · 5 stk 2,5 ml · 4 stk 5 ml · 4 stk 10 ml"),
                i("2 stk venefloner (rosa, blå, grønn)"),
                i("1 stk pulsoksymeter"),
                i("1 stk temperaturmåler (aksillært)"),
                i("1 stk Ringer-acetat"),
                i("2 stk Intrafix"),
                i("1 stk manuell blodtrykksmåler"),
                i("1 stk stetoskop"),
                i("1 stk pupillelykt"),
                i("1 stk redningsfolie"),
                i("2 stk mini spike filter"),
                i("4 stk Eclipse needle"),
                i("5 stk Tegaderm i.v.-bandasje"),
                i("5 stk Combi stopper", "Propp til kanyler, sprøyter o.l."),
                i("1 stk The Morgan lens (øyeskyllelinse)"),
                i("1 stk stor + 1 liten saks"),
                i("5 stk Natriumklorid 30 ml"),
                i("5 stk Natriumklorid 10 ml"),
                i("1 stk pocketmask"),
                i("1 stk barneleke"),
            )

            val brann = template("seed-brann", "Branntaske", TemplateType.BAG, daily)
            items(
                brann,
                i("Burnshield", "1 stk 60x40 · 2 stk 20x45 · 2 stk 20x20 · 2 stk 10x10"),
                i("1 stk Burn aid brann gel spray"),
                i("3 stk Idealbind"),
                i("2 stk redningsfolie"),
                i("1 stk Ringer-acetat"),
                i("2 stk tape"),
                i("5 stk Melolin"),
            )

            val fode = template("seed-fode", "Fødekoffert", TemplateType.BAG, daily)
            items(
                fode,
                i("1 stk Emergency delivery set"),
                i("1 stk baby wrap"),
                i("1 stk maske/bag barn"),
                i("1 stk oksygenslange"),
                i("1 stk pose med div. svelgtuber"),
                i("2 stk sterile hansker"),
                i("1 stk saks"),
                i("1 stk nasal aspirator"),
                i("1 stk Natriumklorid 120 ml"),
                i("1 stk Aluderm steril aluminisert kompress"),
                i("1 stk kompresjonsbandasje"),
                i("Desinfeksjonsmiddel"),
            )

            // ============ Ukentlig og månedlig (plassholdere – redigeres i appen) ============
            val weekly = template("seed-weekly", "Ukentlig sjekk", TemplateType.WEEKLY)
            items(
                weekly,
                i("Full vask av kjøretøy"),
                i("Kontroll av utløpsdatoer forbruksmateriell"),
                i("Lading av reserveutstyr/batterier"),
                i("Etterfylling fra stasjonslager"),
            )

            val monthly = template("seed-monthly", "Månedlig sjekk", TemplateType.MONTHLY)
            items(
                monthly,
                i("Gjennomgang beredskapsplan og tiltakskort"),
                i("Hjertestarter – elektroder og batteri (utløpsdato)"),
                v("Oksygenflasker – sertifisering og nivå", "bar"),
                i("Inventar kontrollert mot innholdslister"),
            )

            // ============ Standardlenker (URL settes i appen) ============
            db.appLinkQueries.insertLink("seed-link-avvik", "Avviksmelding (Forms)", "", 0, currentTimeMillis())
            db.appLinkQueries.insertLink("seed-link-naloxon", "Registrering bruk av naloxon", "", 1, currentTimeMillis())
            db.appLinkQueries.insertLink("seed-link-medisin", "Registrering utdeling/bruk av medisiner", "", 2, currentTimeMillis())
        }
    }

    /** Oppretter mal med fast id. Må kalles inne i transaksjonen. */
    private fun template(id: String, name: String, type: TemplateType, parentId: String? = null): String {
        db.checklistTemplateQueries.insertTemplate(id, name, type.db, parentId, 0, currentTimeMillis())
        return id
    }

    /** Legger inn punkter med stigende sortOrder og faste id-er. Må kalles inne i transaksjonen. */
    private fun items(templateId: String, vararg specs: ItemSpec) {
        specs.forEachIndexed { index, spec ->
            db.checklistItemQueries.insertItem(
                "$templateId-item-${index + 1}", templateId, spec.title, spec.description,
                if (spec.unit != null) 1L else 0L, spec.unit,
                spec.min, spec.max,
                (index + 1).toLong(),
                currentTimeMillis(),
            )
        }
    }

    private class ItemSpec(
        val title: String,
        val description: String? = null,
        val unit: String? = null,
        val min: Double? = null,
        val max: Double? = null,
    )

    /** Vanlig ja/nei-punkt, med valgfri beskrivelse (plassering o.l.). */
    private fun i(title: String, description: String? = null) = ItemSpec(title, description)

    /** Punkt som krever avlest verdi. Grenser kan settes i appen. */
    private fun v(title: String, unit: String, min: Double? = null, max: Double? = null) =
        ItemSpec(title, null, unit, min, max)
}
