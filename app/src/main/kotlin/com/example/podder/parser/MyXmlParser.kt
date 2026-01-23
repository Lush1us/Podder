package com.example.podder.parser

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

class MyXmlParser {
    private val ns: String? = null

    fun parse(inputStream: InputStream): Podcast {
        inputStream.use {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(it, null)
            parser.nextTag()
            return readFeed(parser)
        }
    }

    private fun readFeed(parser: XmlPullParser): Podcast {
        var title: String? = null
        val episodes = mutableListOf<Episode>()

        parser.require(XmlPullParser.START_TAG, ns, "rss")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            if (parser.name == "channel") {
                parser.require(XmlPullParser.START_TAG, ns, "channel")
                while (parser.next() != XmlPullParser.END_TAG) {
                    if (parser.eventType != XmlPullParser.START_TAG) {
                        continue
                    }
                    when (parser.name) {
                        "title" -> title = readTitle(parser)
                        "item" -> episodes.add(readItem(parser))
                        else -> skip(parser)
                    }
                }
            }
        }
        return Podcast(title ?: "awesome podcast", episodes = episodes)
    }

    private fun readItem(parser: XmlPullParser): Episode {
        parser.require(XmlPullParser.START_TAG, ns, "item")
        var title: String? = null
        var description: String? = null
        var pubDate: String? = null
        var enclosure: Enclosure? = null
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            when (parser.name) {
                "title" -> title = readTitle(parser)
                "description" -> description = readDescription(parser)
                "pubDate" -> pubDate = readPubDate(parser)
                "enclosure" -> enclosure = readEnclosure(parser)
                else -> skip(parser)
            }
        }
        return Episode(title ?: "awesome title", description, pubDate, enclosure)
    }

    private fun readTitle(parser: XmlPullParser): String {
        parser.require(XmlPullParser.START_TAG, ns, "title")
        val title = readText(parser)
        parser.require(XmlPullParser.END_TAG, ns, "title")
        return title
    }

    private fun readDescription(parser: XmlPullParser): String {
        parser.require(XmlPullParser.START_TAG, ns, "description")
        val description = readText(parser)
        parser.require(XmlPullParser.END_TAG, ns, "description")
        return description
    }

    private fun readPubDate(parser: XmlPullParser): String {
        parser.require(XmlPullParser.START_TAG, ns, "pubDate")
        val pubDate = readText(parser)
        parser.require(XmlPullParser.END_TAG, ns, "pubDate")
        return pubDate
    }

    private fun readEnclosure(parser: XmlPullParser): Enclosure {
        parser.require(XmlPullParser.START_TAG, ns, "enclosure")
        val url = parser.getAttributeValue(null, "url")
        val type = parser.getAttributeValue(null, "type")
        parser.nextTag()
        parser.require(XmlPullParser.END_TAG, ns, "enclosure")
        return Enclosure(url, type)
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
}
