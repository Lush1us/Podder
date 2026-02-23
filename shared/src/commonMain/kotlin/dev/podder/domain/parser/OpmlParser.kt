package dev.podder.domain.parser

private val xmlUrlRegex = Regex("""xmlUrl="([^"]+)"""")

/**
 * Extracts all RSS feed URLs from an OPML document.
 * Uses a flat regex scan — handles both flat and nested OPML structures.
 */
fun parseOpmlUrls(content: String): List<String> =
    xmlUrlRegex.findAll(content)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
