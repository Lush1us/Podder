package com.example.podder.parser

import com.example.podder.data.local.SubscriptionEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

object OpmlParser {
    suspend fun parse(inputStream: InputStream): List<SubscriptionEntity> {
        val subscriptions = mutableListOf<SubscriptionEntity>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "outline") {
                // Extract xmlUrl and title/text attributes
                val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                val title = parser.getAttributeValue(null, "title")
                    ?: parser.getAttributeValue(null, "text")

                // Only add if xmlUrl is present (some outlines might be categories)
                if (xmlUrl != null) {
                    subscriptions.add(
                        SubscriptionEntity(
                            url = xmlUrl,
                            title = title,
                            dateAdded = System.currentTimeMillis()
                        )
                    )
                }
            }
            eventType = parser.next()
        }

        return subscriptions
    }
}
