package org.example.project.util

/** Millisekunder siden epoch – plattformspesifikk implementasjon. */
expect fun currentTimeMillis(): Long

/**
 * Periodegrensene de faste kontrollene måles mot.
 *
 * Alle tre er kalenderbaserte, ikke glidende vinduer: en ukessjekk gjort
 * mandag skal gjelde ut uka, ikke i sju døgn fra klokkeslettet den ble
 * signert. Månedlig var tidligere «siste 30 døgn», som ga ulik lengde på
 * måneder og et skille midt i måneden.
 *
 * Implementasjonene bruker plattformens kalender, så sommertid håndteres
 * riktig – et døgn er ikke alltid 86 400 000 millisekunder.
 */

/** Millisekunder ved midnatt i dag, lokal tid. */
expect fun startOfTodayMillis(): Long

/** Millisekunder ved midnatt mandag denne uka, lokal tid. */
expect fun startOfWeekMillis(): Long

/** Millisekunder ved midnatt den 1. denne måneden, lokal tid. */
expect fun startOfMonthMillis(): Long
