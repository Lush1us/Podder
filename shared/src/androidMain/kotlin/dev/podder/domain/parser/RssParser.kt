package dev.podder.domain.parser

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Locale

actual fun parseRssFeed(xml: String): RssFeed {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(xml.reader())

    var feedTitle = ""
    var feedDescription = ""
    var feedImageUrl: String? = null
    val items = mutableListOf<RssItem>()

    var inItem = false
    var inImage = false
    var currentTag = ""

    var itemGuid = ""
    var itemTitle = ""
    var itemUrl = ""
    var itemDescription = ""
    var itemPubDate = ""
    var itemDuration = ""

    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            XmlPullParser.START_TAG -> {
                currentTag = parser.name
                when (currentTag) {
                    "item", "entry" -> { inItem = true; inImage = false }
                    "image"         -> { inImage = true }
                    "enclosure"     -> if (inItem) itemUrl = parser.getAttributeValue(null, "url") ?: ""
                }
            }
            XmlPullParser.END_TAG -> {
                when (parser.name) {
                    "item", "entry" -> {
                        if (itemGuid.isNotBlank() || itemUrl.isNotBlank()) {
                            items += RssItem(
                                guid        = itemGuid.ifBlank { itemUrl },
                                title       = itemTitle,
                                url         = itemUrl,
                                description = itemDescription,
                                pubDateUtc  = parseRfc822(itemPubDate),
                                durationMs  = parseItunesDuration(itemDuration),
                            )
                        }
                        inItem = false
                        itemGuid = ""; itemTitle = ""; itemUrl = ""; itemDescription = ""
                        itemPubDate = ""; itemDuration = ""
                    }
                    "image" -> inImage = false
                }
                currentTag = ""
            }
            XmlPullParser.TEXT -> {
                val text = parser.text?.trim() ?: ""
                when (currentTag) {
                    "title"                       -> if (inItem) itemTitle = text else if (!inImage) feedTitle = text
                    "description", "summary"      -> if (inItem) itemDescription = text else if (!inImage) feedDescription = text
                    "guid", "id"                  -> if (inItem) itemGuid = text
                    "pubDate", "published",
                    "updated"                     -> if (inItem) itemPubDate = text
                    "itunes:duration", "duration" -> if (inItem) itemDuration = text
                    "url"                         -> if (inImage) feedImageUrl = text
                }
            }
        }
        eventType = parser.next()
    }

    return RssFeed(feedTitle, feedDescription, feedImageUrl, items)
}

private fun parseRfc822(date: String): Long {
    if (date.isBlank()) return 0L
    return try {
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "dd MMM yyyy HH:mm:ss Z",
        )
        for (fmt in formats) {
            runCatching {
                return SimpleDateFormat(fmt, Locale.ENGLISH).parse(date)!!.time / 1000
            }
        }
        0L
    } catch (e: Exception) { 0L }
}

private fun parseItunesDuration(duration: String): Long {
    if (duration.isBlank()) return 0L
    val parts = duration.split(":").map { it.toLongOrNull() ?: 0L }
    return when (parts.size) {
        1    -> parts[0] * 1000
        2    -> (parts[0] * 60 + parts[1]) * 1000
        3    -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
        else -> 0L
    }
}
