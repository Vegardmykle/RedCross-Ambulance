package org.example.project.util

import java.util.Calendar

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

/** Dagens dato med klokkeslettet nullstilt – utgangspunkt for alle grensene. */
private fun startOfToday(): Calendar = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

actual fun startOfTodayMillis(): Long = startOfToday().timeInMillis

actual fun startOfWeekMillis(): Long = startOfToday().apply {
    // Må settes før DAY_OF_WEEK: den avgjør hvilken mandag som regnes som
    // ukas første dag. Med søndag som første dag ville en kontroll gjort
    // søndag havnet i neste uke.
    firstDayOfWeek = Calendar.MONDAY
    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
}.timeInMillis

actual fun startOfMonthMillis(): Long = startOfToday().apply {
    set(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis
