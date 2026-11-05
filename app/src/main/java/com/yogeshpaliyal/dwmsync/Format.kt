package com.yogeshpaliyal.dwmsync

import android.text.format.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val dateTimeFmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val dayThisYear = DateTimeFormatter.ofPattern("EEE, d MMM")
private val dayOtherYear = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

val zone: ZoneId get() = ZoneId.systemDefault()

fun localDate(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

fun formatDate(i: Instant): String = dateFmt.format(i.atZone(zone))

fun formatDate(d: LocalDate): String = dateFmt.format(d)

fun formatDateTime(epochMillis: Long): String = dateTimeFmt.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

fun formatTime(epochMillis: Long): String = timeFmt.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

/** "Today", "Yesterday", "Tue, 3 Nov", or with the year when it isn't this year. */
fun formatDayHeader(d: LocalDate, today: LocalDate = LocalDate.now(zone)): String = when {
    d == today -> "Today"
    d == today.minusDays(1) -> "Yesterday"
    d.year == today.year -> dayThisYear.format(d)
    else -> dayOtherYear.format(d)
}

/** "just now", "5 minutes ago", "2 hours ago", "yesterday"… */
fun formatRelative(epochMillis: Long, now: Long = System.currentTimeMillis()): String =
    if (now - epochMillis < DateUtils.MINUTE_IN_MILLIS) "just now"
    else DateUtils.getRelativeTimeSpanString(epochMillis, now, DateUtils.MINUTE_IN_MILLIS).toString().lowercase(Locale.getDefault())

fun formatDistance(km: Double): String = String.format(Locale.getDefault(), "%.2f km", km)

fun formatDuration(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

fun plural(n: Int, one: String, many: String) = if (n == 1) "$n $one" else "$n $many"
