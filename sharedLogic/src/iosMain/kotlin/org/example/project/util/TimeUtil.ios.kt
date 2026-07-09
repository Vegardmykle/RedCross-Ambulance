package org.example.project.util

import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun startOfTodayMillis(): Long {
    val start = NSCalendar.currentCalendar.startOfDayForDate(NSDate())
    return (start.timeIntervalSince1970 * 1000).toLong()
}
