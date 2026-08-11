package org.example.project.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.example.project.db.AppDatabase
import org.example.project.model.TemplateType

/**
 * Felles oppsett for enhetstestene: en tom SQLite-database i minnet.
 * Ingen emulator, ingen Firebase – hver test får sin egen rene database.
 */
fun inMemoryDatabase(): AppDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    AppDatabase.Schema.create(driver)
    return AppDatabase(driver)
}

/**
 * Et minimalt, realistisk oppsett: én ambulanse, ett mannskap og
 * en daglig sjekkliste. Testene legger til punkter selv.
 */
class TestScenario(val db: AppDatabase = inMemoryDatabase()) {
    val repo = ChecklistRepository(db)

    lateinit var ambulanceId: String
    lateinit var templateId: String

    val userId = "1234"
    val userName = "Kari Nordmann"

    suspend fun setUp(): TestScenario {
        ambulanceId = repo.addAmbulance(callSign = "Ambulanse 1", registrationNumber = "EL12345")
        repo.addUser(id = userId, name = userName, role = "Ambulansearbeider")
        templateId = repo.createTemplate(name = "Sjekkliste før vakt", type = TemplateType.DAILY)
        return this
    }

    /** Vanlig ja/nei-punkt. */
    suspend fun addItem(title: String, templateId: String = this.templateId): String =
        repo.addItem(templateId = templateId, title = title)

    /** Målepunkt med grenseverdier, f.eks. oksygentrykk 180–250 bar. */
    suspend fun addMeasurementItem(
        title: String,
        unit: String = "bar",
        min: Double? = null,
        max: Double? = null,
        templateId: String = this.templateId,
    ): String = repo.addItem(
        templateId = templateId,
        title = title,
        requiresValue = true,
        unit = unit,
        minValue = min,
        maxValue = max,
    )

    /** Sekk/taske under hovedlista. */
    suspend fun addBag(name: String): String =
        repo.createTemplate(name = name, type = TemplateType.BAG, parentId = templateId)

    suspend fun startRun(): String =
        repo.startOrResumeRun(templateId = templateId, ambulanceId = ambulanceId).id

    fun responseFor(runId: String, itemId: String) =
        db.checklistResponseQueries.getResponsesForRun(runId)
            .executeAsList()
            .first { it.itemId == itemId }

    fun run(runId: String) = db.checklistRunQueries.getRunById(runId).executeAsOne()
}

suspend fun scenario(): TestScenario = TestScenario().setUp()
