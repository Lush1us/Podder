package com.example.podder.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.podder.data.local.EpisodeEntity
import com.example.podder.data.local.PodcastDao
import com.example.podder.data.local.PodcastEntity
import com.example.podder.data.local.PodderDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PodcastDaoTest {

    private lateinit var database: PodderDatabase
    private lateinit var podcastDao: PodcastDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            PodderDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        podcastDao = database.podcastDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insert_detects_duplicates() = runTest {
        // Given: A podcast and episode
        val podcast = PodcastEntity(
            url = "https://example.com/feed",
            title = "Test Podcast",
            imageUrl = "https://example.com/image.jpg"
        )
        val episode = EpisodeEntity(
            guid = "unique-guid-123",
            podcastUrl = "https://example.com/feed",
            title = "Test Episode",
            description = "Test description",
            pubDate = System.currentTimeMillis(),
            audioUrl = "https://example.com/audio.mp3",
            duration = 3600L,
            progressInMillis = 0L
        )

        // When: Insert podcast first (required for foreign key)
        podcastDao.insertPodcast(podcast)

        // Insert episode twice
        podcastDao.addEpisodes(listOf(episode))
        podcastDao.addEpisodes(listOf(episode)) // Insert same episode again

        // Then: Verify count remains 1 (REPLACE strategy replaces, not duplicates)
        val count = podcastDao.getEpisodeCount()
        assertEquals("ConflictStrategy.REPLACE should not create duplicates", 1, count)
    }

    @Test
    fun insert_withReplace_updatesExistingRecord() = runTest {
        // Given: A podcast and episode
        val podcast = PodcastEntity(
            url = "https://example.com/feed",
            title = "Test Podcast",
            imageUrl = "https://example.com/image.jpg"
        )
        val originalEpisode = EpisodeEntity(
            guid = "episode-guid",
            podcastUrl = "https://example.com/feed",
            title = "Original Title",
            description = "Original description",
            pubDate = System.currentTimeMillis(),
            audioUrl = "https://example.com/audio.mp3",
            duration = 3600L,
            progressInMillis = 0L
        )

        podcastDao.insertPodcast(podcast)
        podcastDao.addEpisodes(listOf(originalEpisode))

        // When: Insert episode with same GUID but different data
        val updatedEpisode = originalEpisode.copy(
            title = "Updated Title",
            progressInMillis = 5000L
        )
        podcastDao.addEpisodes(listOf(updatedEpisode))

        // Then: Verify the episode was updated
        val retrieved = podcastDao.getEpisode("episode-guid")
        assertNotNull(retrieved)
        assertEquals("Updated Title", retrieved?.title)
        assertEquals(5000L, retrieved?.progressInMillis)
    }

    @Test
    fun updateLocalFile_persists_path() = runTest {
        // Given: A podcast and episode
        val podcast = PodcastEntity(
            url = "https://example.com/feed",
            title = "Test Podcast",
            imageUrl = null
        )
        val episode = EpisodeEntity(
            guid = "download-test-guid",
            podcastUrl = "https://example.com/feed",
            title = "Download Test Episode",
            description = "Test description",
            pubDate = System.currentTimeMillis(),
            audioUrl = "https://example.com/audio.mp3",
            duration = 1800L,
            progressInMillis = 0L,
            localFilePath = null
        )

        podcastDao.insertPodcast(podcast)
        podcastDao.addEpisodes(listOf(episode))

        // When: Update local file path
        val localPath = "/path/to/file.mp3"
        podcastDao.updateLocalFile("download-test-guid", localPath)

        // Then: Retrieve episode and assert localFilePath is set
        val retrieved = podcastDao.getEpisode("download-test-guid")
        assertNotNull(retrieved)
        assertEquals(
            "localFilePath should be updated and persisted",
            localPath,
            retrieved?.localFilePath
        )
    }

    @Test
    fun clearLocalFile_removesPath() = runTest {
        // Given: An episode with a local file path
        val podcast = PodcastEntity(
            url = "https://example.com/feed",
            title = "Test Podcast",
            imageUrl = null
        )
        val episode = EpisodeEntity(
            guid = "clear-test-guid",
            podcastUrl = "https://example.com/feed",
            title = "Clear Test Episode",
            description = "Test description",
            pubDate = System.currentTimeMillis(),
            audioUrl = "https://example.com/audio.mp3",
            duration = 1800L,
            progressInMillis = 0L,
            localFilePath = "/path/to/downloaded/file.mp3"
        )

        podcastDao.insertPodcast(podcast)
        podcastDao.addEpisodes(listOf(episode))

        // Verify it was set
        val beforeClear = podcastDao.getEpisode("clear-test-guid")
        assertNotNull(beforeClear?.localFilePath)

        // When: Clear the local file path
        podcastDao.clearLocalFile("clear-test-guid")

        // Then: Verify localFilePath is null
        val afterClear = podcastDao.getEpisode("clear-test-guid")
        assertNull(
            "localFilePath should be null after clearLocalFile",
            afterClear?.localFilePath
        )
    }

    @Test
    fun foreign_key_cascade() = runTest {
        // Given: Insert Podcast and Episode
        val podcast = PodcastEntity(
            url = "https://cascade-test.com/feed",
            title = "Cascade Test Podcast",
            imageUrl = null
        )
        val episode1 = EpisodeEntity(
            guid = "cascade-episode-1",
            podcastUrl = "https://cascade-test.com/feed",
            title = "Cascade Episode 1",
            description = "Description",
            pubDate = System.currentTimeMillis(),
            audioUrl = "https://cascade-test.com/e1.mp3",
            duration = 1200L,
            progressInMillis = 0L
        )
        val episode2 = EpisodeEntity(
            guid = "cascade-episode-2",
            podcastUrl = "https://cascade-test.com/feed",
            title = "Cascade Episode 2",
            description = "Description",
            pubDate = System.currentTimeMillis(),
            audioUrl = "https://cascade-test.com/e2.mp3",
            duration = 1500L,
            progressInMillis = 0L
        )

        podcastDao.insertPodcast(podcast)
        podcastDao.addEpisodes(listOf(episode1, episode2))

        // Verify episodes were inserted
        val beforeDelete = podcastDao.getEpisodeCount()
        assertEquals(2, beforeDelete)

        // When: Delete Podcast using raw SQL (since we don't have a delete method)
        database.runInTransaction {
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM podcasts WHERE url = 'https://cascade-test.com/feed'"
            )
        }

        // Then: Assert Episodes are deleted (cascade check)
        val afterDelete = podcastDao.getEpisodeCount()
        assertEquals(
            "Episodes should be deleted when parent podcast is deleted (CASCADE)",
            0,
            afterDelete
        )
    }

    @Test
    fun updateProgress_updatesCorrectEpisode() = runTest {
        // Given: Multiple episodes
        val podcast = PodcastEntity(
            url = "https://example.com/feed",
            title = "Test Podcast",
            imageUrl = null
        )
        val episode1 = EpisodeEntity(
            guid = "progress-episode-1",
            podcastUrl = "https://example.com/feed",
            title = "Episode 1",
            description = "Description",
            pubDate = System.currentTimeMillis(),
            audioUrl = "https://example.com/e1.mp3",
            duration = 3600L,
            progressInMillis = 0L
        )
        val episode2 = EpisodeEntity(
            guid = "progress-episode-2",
            podcastUrl = "https://example.com/feed",
            title = "Episode 2",
            description = "Description",
            pubDate = System.currentTimeMillis(),
            audioUrl = "https://example.com/e2.mp3",
            duration = 1800L,
            progressInMillis = 0L
        )

        podcastDao.insertPodcast(podcast)
        podcastDao.addEpisodes(listOf(episode1, episode2))

        // When: Update progress for episode 1 only
        podcastDao.updateProgress("progress-episode-1", 120000L) // 2 minutes

        // Then: Only episode 1 should have updated progress
        val retrieved1 = podcastDao.getEpisode("progress-episode-1")
        val retrieved2 = podcastDao.getEpisode("progress-episode-2")

        assertEquals(120000L, retrieved1?.progressInMillis)
        assertEquals(0L, retrieved2?.progressInMillis)
    }

    @Test
    fun getAllProgress_returnsAllProgressData() = runTest {
        // Given: Episodes with various progress states
        val podcast = PodcastEntity(
            url = "https://example.com/feed",
            title = "Test Podcast",
            imageUrl = null
        )
        val episodes = listOf(
            EpisodeEntity("ep-1", "https://example.com/feed", "Ep 1", "", System.currentTimeMillis(), "", 3600L, 1000L),
            EpisodeEntity("ep-2", "https://example.com/feed", "Ep 2", "", System.currentTimeMillis(), "", 1800L, 0L),
            EpisodeEntity("ep-3", "https://example.com/feed", "Ep 3", "", System.currentTimeMillis(), "", 2400L, 5000L)
        )

        podcastDao.insertPodcast(podcast)
        podcastDao.addEpisodes(episodes)

        // When: Get all progress
        val progressList = podcastDao.getAllProgress()

        // Then: All episodes should be returned with their progress
        assertEquals(3, progressList.size)

        val progressMap = progressList.associate { it.guid to it.progressInMillis }
        assertEquals(1000L, progressMap["ep-1"])
        assertEquals(0L, progressMap["ep-2"])
        assertEquals(5000L, progressMap["ep-3"])
    }

    @Test
    fun getAllDownloads_returnsOnlyDownloadedEpisodes() = runTest {
        // Given: Some episodes downloaded, some not
        val podcast = PodcastEntity(
            url = "https://example.com/feed",
            title = "Test Podcast",
            imageUrl = null
        )
        val episodes = listOf(
            EpisodeEntity("dl-1", "https://example.com/feed", "Downloaded 1", "", System.currentTimeMillis(), "", 3600L, 0L, "/path/1.mp3"),
            EpisodeEntity("not-dl", "https://example.com/feed", "Not Downloaded", "", System.currentTimeMillis(), "", 1800L, 0L, null),
            EpisodeEntity("dl-2", "https://example.com/feed", "Downloaded 2", "", System.currentTimeMillis(), "", 2400L, 0L, "/path/2.mp3")
        )

        podcastDao.insertPodcast(podcast)
        podcastDao.addEpisodes(episodes)

        // When: Get all downloads
        val downloads = podcastDao.getAllDownloads()

        // Then: Only downloaded episodes should be returned
        assertEquals(2, downloads.size)
        assertTrue(downloads.all { it.localFilePath != null })
        assertTrue(downloads.none { it.guid == "not-dl" })
    }

    @Test
    fun getExpiredDownloads_filtersCorrectly() = runTest {
        // Given: Episodes with various finishedAt times
        val podcast = PodcastEntity(
            url = "https://example.com/feed",
            title = "Test Podcast",
            imageUrl = null
        )

        val now = System.currentTimeMillis()
        val twelveHoursAgo = now - (12 * 60 * 60 * 1000)
        val thirtyHoursAgo = now - (30 * 60 * 60 * 1000)

        val episodes = listOf(
            // Downloaded and finished 30 hours ago - should be expired
            EpisodeEntity("expired-1", "https://example.com/feed", "Expired", "", now, "", 3600L, 0L, "/path/expired.mp3", thirtyHoursAgo),
            // Downloaded and finished 12 hours ago - should not be expired
            EpisodeEntity("not-expired", "https://example.com/feed", "Not Expired", "", now, "", 1800L, 0L, "/path/recent.mp3", twelveHoursAgo),
            // Downloaded but not finished - should not be expired
            EpisodeEntity("not-finished", "https://example.com/feed", "Not Finished", "", now, "", 2400L, 0L, "/path/playing.mp3", null),
            // Not downloaded - should not be expired
            EpisodeEntity("not-downloaded", "https://example.com/feed", "Not Downloaded", "", now, "", 1200L, 0L, null, thirtyHoursAgo)
        )

        podcastDao.insertPodcast(podcast)
        podcastDao.addEpisodes(episodes)

        // When: Get expired downloads (cutoff = 24 hours ago)
        val cutoffTime = now - (24 * 60 * 60 * 1000)
        val expiredDownloads = podcastDao.getExpiredDownloads(cutoffTime)

        // Then: Only the episode finished > 24 hours ago with a local file should be returned
        assertEquals(1, expiredDownloads.size)
        assertEquals("expired-1", expiredDownloads.first().guid)
    }

    @Test
    fun updateFinishedAt_setsTimestamp() = runTest {
        // Given: An episode
        val podcast = PodcastEntity(
            url = "https://example.com/feed",
            title = "Test Podcast",
            imageUrl = null
        )
        val episode = EpisodeEntity(
            guid = "finish-test",
            podcastUrl = "https://example.com/feed",
            title = "Finish Test",
            description = "",
            pubDate = System.currentTimeMillis(),
            audioUrl = "",
            duration = 3600L,
            progressInMillis = 0L,
            finishedAt = null
        )

        podcastDao.insertPodcast(podcast)
        podcastDao.addEpisodes(listOf(episode))

        // When: Update finishedAt
        val finishTime = System.currentTimeMillis()
        podcastDao.updateFinishedAt("finish-test", finishTime)

        // Then: Verify finishedAt is set
        val retrieved = podcastDao.getEpisode("finish-test")
        assertEquals(finishTime, retrieved?.finishedAt)
    }

    @Test
    fun getEpisodesByPodcast_returnsCorrectEpisodes() = runTest {
        // Given: Two podcasts with different episodes
        val podcast1 = PodcastEntity("https://podcast1.com/feed", "Podcast 1", null)
        val podcast2 = PodcastEntity("https://podcast2.com/feed", "Podcast 2", null)

        val episodesP1 = listOf(
            EpisodeEntity("p1-e1", "https://podcast1.com/feed", "P1 Episode 1", "", System.currentTimeMillis(), "", 3600L, 0L),
            EpisodeEntity("p1-e2", "https://podcast1.com/feed", "P1 Episode 2", "", System.currentTimeMillis() - 1000, "", 1800L, 0L)
        )
        val episodesP2 = listOf(
            EpisodeEntity("p2-e1", "https://podcast2.com/feed", "P2 Episode 1", "", System.currentTimeMillis(), "", 2400L, 0L)
        )

        podcastDao.insertPodcast(podcast1)
        podcastDao.insertPodcast(podcast2)
        podcastDao.addEpisodes(episodesP1 + episodesP2)

        // When: Get episodes for podcast 1
        val result = podcastDao.getEpisodesByPodcast("https://podcast1.com/feed").first()

        // Then: Only podcast 1's episodes should be returned
        assertEquals(2, result.size)
        assertTrue(result.all { it.episode.podcastUrl == "https://podcast1.com/feed" })
    }
}
