package org.example.project.util

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun startOfTodayMillis(): Long =
    java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
