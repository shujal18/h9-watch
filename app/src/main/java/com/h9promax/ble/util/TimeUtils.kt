package com.h9promax.ble.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Time formatting helpers for log timestamps.
 */
object TimeUtils {

    private val fullFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @JvmStatic
    fun now(): String = fullFormat.format(Date())

    @JvmStatic
    fun nowMillis(): String = timeFormat.format(Date())

    /**
     * Format a given epoch millis as a full timestamp.
     */
    fun format(millis: Long): String = fullFormat.format(Date(millis))
}