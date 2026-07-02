package org.example.project.storage

/**
 * Lokal lagring av PDF-dokumenter (instrukser, beredskapsplan, tiltakskort).
 * Filene ligger i appens eget område og er tilgjengelige uten nett.
 * Stien som returneres fra [save] lagres i Document-tabellen (uri).
 */
interface DocumentStorage {

    /** Lagrer bytes som fil og returnerer absolutt lokal sti. Overskriver eksisterende. */
    fun save(fileName: String, bytes: ByteArray): String

    fun exists(path: String): Boolean

    fun delete(path: String)

    /** Åpner PDF i plattformens innebygde viser. Returnerer false om fila mangler eller ingen viser finnes. */
    fun openPdf(path: String): Boolean
}
