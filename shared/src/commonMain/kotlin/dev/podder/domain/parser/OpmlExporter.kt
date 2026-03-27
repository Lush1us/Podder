package dev.podder.domain.parser

import dev.podder.data.repository.PodcastSummary

fun generateOpml(podcasts: List<PodcastSummary>): String = buildString {
    appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
    appendLine("""<opml version="2.0">""")
    appendLine("  <head><title>Podder Subscriptions</title></head>")
    appendLine("  <body>")
    for (p in podcasts) {
        val title = p.title.escapeXml()
        val url   = p.rssUrl.escapeXml()
        appendLine("""    <outline text="$title" type="rss" xmlUrl="$url" />""")
    }
    appendLine("  </body>")
    appendLine("</opml>")
}

private fun String.escapeXml(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")
