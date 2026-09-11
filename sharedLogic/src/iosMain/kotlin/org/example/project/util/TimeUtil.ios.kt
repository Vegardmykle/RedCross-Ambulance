package org.example.project.util

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitWeekOfYear
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSCalendarUnitYearForWeekOfYear
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun currentTimeMillis(): Long = NSDate().millis()

private fun NSDate.millis(): Long = (timeIntervalSince1970 * 1000).toLong()

actual fun startOfTodayMillis(): Long =
    NSCalendar.currentCalendar.startOfDayForDate(NSDate()).millis()

actual fun startOfWeekMillis(): Long {
    val calendar = NSCalendar.currentCalendar
    // 2 = mandag. Uten dette følger uka enhetens regionsinnstilling, og en
    // kontroll gjort søndag ville havnet i neste uke på en enhet satt til
    // amerikansk kalender.
    calendar.firstWeekday = 2uL
    val components = calendar.components(
        NSCalendarUnitYearForWeekOfYear or NSCalendarUnitWeekOfYear,
        fromDate = NSDate(),
    )
    return calendar.dateFromComponents(components)?.millis() ?: startOfTodayMillis()
}

actual fun startOfMonthMillis(): Long {
    val calendar = NSCalendar.currentCalendar
    val components = calendar.components(
        NSCalendarUnitYear or NSCalendarUnitMonth,
        fromDate = NSDate(),
    )
    return calendar.dateFromComponents(components)?.millis() ?: startOfTodayMillis()
}
