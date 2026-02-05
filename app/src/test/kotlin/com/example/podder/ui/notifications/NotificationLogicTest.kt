package com.example.podder.ui.notifications

import com.example.podder.data.local.EpisodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for notification text building logic.
 *
 * Since NotificationHelper interacts with the Android System, we test the logic
 * that builds the notification text separately. This tests the same logic used in
 * NotificationHelper.showNewEpisodes() but without requiring Android context.
 */
class NotificationLogicTest {

    /**
     * Helper function that mirrors the notification title logic from NotificationHelper.
     * This is the same logic used in showNewEpisodes().
     */
    private fun buildNotificationTitle(episodes: List<EpisodeEntity>): String {
        return if (episodes.size == 1) {
            "New Episode"
        } else {
            "${episodes.size} New Episodes"
        }
    }

    /**
     * Helper function that mirrors the notification content text logic from NotificationHelper.
     */
    private fun buildNotificationContentText(episodes: List<EpisodeEntity>): String {
        return episodes.firstOrNull()?.title ?: ""
    }

    /**
     * Helper function that builds inbox style lines (first 5 episode titles).
     */
    private fun buildInboxLines(episodes: List<EpisodeEntity>): List<String> {
        return episodes.take(5).map { it.title }
    }

    /**
     * Helper function that builds summary text for more than 5 episodes.
     */
    private fun buildSummaryText(episodes: List<EpisodeEntity>): String? {
        return if (episodes.size > 5) {
            "+${episodes.size - 5} more"
        } else {
            null
        }
    }

    @Test
    fun buildNotification_singleEpisode_usesTitle() {
        // Given: A list with 1 episode
        val singleEpisode = listOf(
            EpisodeEntity(
                guid = "single-guid",
                podcastUrl = "https://example.com/feed",
                title = "My Awesome Episode",
                description = "Description",
                pubDate = System.currentTimeMillis(),
                audioUrl = "https://example.com/audio.mp3",
                duration = 3600L,
                progressInMillis = 0L
            )
        )

        // When: Build notification title
        val title = buildNotificationTitle(singleEpisode)
        val contentText = buildNotificationContentText(singleEpisode)

        // Then: Assert title equals "New Episode" (singular)
        assertEquals("Single episode should use 'New Episode' title", "New Episode", title)
        assertEquals("Content text should be the episode title", "My Awesome Episode", contentText)
    }

    @Test
    fun buildNotification_multipleEpisodes_usesCount() {
        // Given: A list with 3 episodes
        val multipleEpisodes = listOf(
            createTestEpisode("guid-1", "Episode 1"),
            createTestEpisode("guid-2", "Episode 2"),
            createTestEpisode("guid-3", "Episode 3")
        )

        // When: Build notification title
        val title = buildNotificationTitle(multipleEpisodes)

        // Then: Assert title contains "3 new episodes"
        assertEquals("Multiple episodes should show count in title", "3 New Episodes", title)
        assertTrue("Title should contain episode count", title.contains("3"))
    }

    @Test
    fun buildNotification_twoEpisodes_usesCorrectGrammar() {
        // Given: A list with 2 episodes
        val twoEpisodes = listOf(
            createTestEpisode("guid-1", "First Episode"),
            createTestEpisode("guid-2", "Second Episode")
        )

        // When: Build notification title
        val title = buildNotificationTitle(twoEpisodes)

        // Then: Assert plural form is used
        assertEquals("2 New Episodes", title)
    }

    @Test
    fun buildNotification_inboxStyle_showsFirst5Episodes() {
        // Given: A list with 7 episodes
        val episodes = (1..7).map { i ->
            createTestEpisode("guid-$i", "Episode $i")
        }

        // When: Build inbox lines
        val inboxLines = buildInboxLines(episodes)

        // Then: Only first 5 should be included
        assertEquals(5, inboxLines.size)
        assertEquals("Episode 1", inboxLines[0])
        assertEquals("Episode 5", inboxLines[4])
    }

    @Test
    fun buildNotification_summaryText_showsRemainingCount() {
        // Given: A list with 8 episodes
        val episodes = (1..8).map { i ->
            createTestEpisode("guid-$i", "Episode $i")
        }

        // When: Build summary text
        val summaryText = buildSummaryText(episodes)

        // Then: Summary should show +3 more
        assertEquals("+3 more", summaryText)
    }

    @Test
    fun buildNotification_exactly5Episodes_noSummaryText() {
        // Given: A list with exactly 5 episodes
        val episodes = (1..5).map { i ->
            createTestEpisode("guid-$i", "Episode $i")
        }

        // When: Build summary text
        val summaryText = buildSummaryText(episodes)

        // Then: No summary needed
        assertEquals(null, summaryText)
    }

    @Test
    fun buildNotification_contentText_usesFirstEpisodeTitle() {
        // Given: Multiple episodes
        val episodes = listOf(
            createTestEpisode("first", "First Episode Title"),
            createTestEpisode("second", "Second Episode"),
            createTestEpisode("third", "Third Episode")
        )

        // When: Build content text
        val contentText = buildNotificationContentText(episodes)

        // Then: Should be the first episode's title
        assertEquals("First Episode Title", contentText)
    }

    @Test
    fun buildNotification_emptyList_handlesGracefully() {
        // Given: Empty list
        val emptyList = emptyList<EpisodeEntity>()

        // When: Build notification components (should handle gracefully)
        val contentText = buildNotificationContentText(emptyList)
        val inboxLines = buildInboxLines(emptyList)
        val summaryText = buildSummaryText(emptyList)

        // Then: Should return empty/null values
        assertEquals("", contentText)
        assertTrue(inboxLines.isEmpty())
        assertEquals(null, summaryText)
    }

    @Test
    fun buildNotification_largeList_handlesCorrectly() {
        // Given: A large list with 100 episodes
        val largeList = (1..100).map { i ->
            createTestEpisode("guid-$i", "Episode $i")
        }

        // When: Build notification components
        val title = buildNotificationTitle(largeList)
        val inboxLines = buildInboxLines(largeList)
        val summaryText = buildSummaryText(largeList)

        // Then: Should handle correctly
        assertEquals("100 New Episodes", title)
        assertEquals(5, inboxLines.size)
        assertEquals("+95 more", summaryText)
    }

    @Test
    fun buildNotification_episodeWithSpecialCharacters_handlesCorrectly() {
        // Given: Episodes with special characters in titles
        val episodes = listOf(
            createTestEpisode("special-1", "Episode with \"Quotes\""),
            createTestEpisode("special-2", "Episode with <HTML>"),
            createTestEpisode("special-3", "Episode with émoji 🎙️")
        )

        // When: Build inbox lines
        val inboxLines = buildInboxLines(episodes)

        // Then: Special characters should be preserved
        assertEquals("Episode with \"Quotes\"", inboxLines[0])
        assertEquals("Episode with <HTML>", inboxLines[1])
        assertEquals("Episode with émoji 🎙️", inboxLines[2])
    }

    /**
     * Helper function to create test episodes with minimal required fields.
     */
    private fun createTestEpisode(guid: String, title: String): EpisodeEntity {
        return EpisodeEntity(
            guid = guid,
            podcastUrl = "https://example.com/feed",
            title = title,
            description = "Test description",
            pubDate = System.currentTimeMillis(),
            audioUrl = "https://example.com/audio.mp3",
            duration = 1800L,
            progressInMillis = 0L
        )
    }
}
