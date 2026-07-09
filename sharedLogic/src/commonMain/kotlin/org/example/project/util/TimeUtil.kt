package org.example.project.util

/** Millisekunder siden epoch – plattformspesifikk implementasjon. */
expect fun currentTimeMillis(): Long

/** Millisekunder ved midnatt i dag, lokal tid. */
expect fun startOfTodayMillis(): Long
