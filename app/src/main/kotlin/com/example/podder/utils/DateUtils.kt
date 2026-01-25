package com.example.podder.utils

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object DateUtils {
    fun parseRssDate(dateString: String): Long {
        return try {
            // RFC_1123_DATE_TIME is for "EEE, dd MMM yyyy HH:mm:ss z"
            // Some RSS feeds might use "Z" for UTC offset, which RFC_1123_DATE_TIME handles.
            // However, some might use "GMT" or other time zone abbreviations.
            // Let's try a more robust approach with multiple formatters if needed,
            // but start with the standard one.
            val formatter = DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH)
            ZonedDateTime.parse(dateString, formatter).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            // Fallback for other common RSS date formats if RFC_1123_DATE_TIME fails
            try {
                val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
                ZonedDateTime.parse(dateString, formatter).toInstant().toEpochMilli()
            } catch (e: DateTimeParseException) {
                try {
                    val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                    ZonedDateTime.parse(dateString, formatter).toInstant().toEpochMilli()
                } catch (e: DateTimeParseException) {
                    System.currentTimeMillis() // Fallback to current time on parse error
                }
            }
        }
    }
}
