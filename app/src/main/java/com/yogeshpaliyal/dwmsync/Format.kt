package com.yogeshpaliyal.dwmsync

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val dateTimeFmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

fun formatDate(i: Instant): String = dateFmt.format(i.atZone(ZoneId.systemDefault()))

fun formatDateTime(epochMillis: Long): String =
    dateTimeFmt.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

fun formatDistance(km: Double): String = String.format(Locale.getDefault(), "%.2f km", km)

fun formatDuration(minutes: Int): String =
    if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
