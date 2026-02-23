package dev.podder.domain.parser

actual fun parseRssFeed(xml: String): RssFeed {
    // TODO: implement via NSXMLParser in a future session on macOS/Xcode
    return RssFeed("", "", null, emptyList())
}
