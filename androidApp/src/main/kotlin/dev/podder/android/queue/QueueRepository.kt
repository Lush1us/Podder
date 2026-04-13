package com.lush1us.podder.queue

import dev.podder.db.PodderDatabase
import dev.podder.logging.LogEvent
import dev.podder.logging.LogLevel
import dev.podder.logging.PodderLogger
import dev.podder.logging.Subsystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QueueEntry(
    val episodeId: String,
    val url: String,
    val title: String,
    val artworkUrl: String?,
    val podcastTitle: String,
    val durationMs: Long,
)

class QueueRepository(
    private val db: PodderDatabase,
    private val logger: PodderLogger,
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _queue = MutableStateFlow<List<QueueEntry>>(emptyList())
    val queue: StateFlow<List<QueueEntry>> = _queue.asStateFlow()

    private val _autoplay = MutableStateFlow(false)
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()

    init {
        val rows = db.queueQueries.selectAllOrdered().executeAsList()
        _queue.value = rows.map { row ->
            QueueEntry(
                episodeId    = row.episodeId,
                url          = row.url,
                title        = row.title,
                artworkUrl   = row.artworkUrl,
                podcastTitle = row.podcastTitle,
                durationMs   = row.durationMs,
            )
        }
    }

    fun addToQueue(entry: QueueEntry) {
        _queue.update { list ->
            if (list.any { it.episodeId == entry.episodeId }) list else {
                val updated = list + entry
                logger.log(LogLevel.INFO, Subsystem.QUEUE,
                    LogEvent.Queue.EpisodeAdded(entry.episodeId, updated.size))
                persistQueue(updated)
                updated
            }
        }
    }

    fun removeFromQueue(episodeId: String) {
        _queue.update { list ->
            val updated = list.filter { e -> e.episodeId != episodeId }
            if (updated.size != list.size) {
                logger.log(LogLevel.INFO, Subsystem.QUEUE,
                    LogEvent.Queue.EpisodeRemoved(episodeId, updated.size))
                persistQueue(updated)
            }
            updated
        }
    }

    /** Return the next entry without removing it, or null if the queue is empty. */
    fun peekNext(): QueueEntry? = _queue.value.firstOrNull()

    /** Remove and return the next entry, or null if the queue is empty. */
    fun takeNext(): QueueEntry? {
        var next: QueueEntry? = null
        _queue.update { list ->
            next = list.firstOrNull()
            if (next != null) {
                val updated = list.drop(1)
                logger.log(LogLevel.INFO, Subsystem.QUEUE,
                    LogEvent.Queue.NextTaken(next!!.episodeId, updated.size))
                persistQueue(updated)
                updated
            } else list
        }
        return next
    }

    fun reorderQueue(from: Int, to: Int) {
        _queue.update { list ->
            if (from == to || from !in list.indices || to !in list.indices) return@update list
            val mutable = list.toMutableList()
            mutable.add(to, mutable.removeAt(from))
            val updated = mutable.toList()
            persistQueue(updated)
            updated
        }
    }

    fun isInQueue(episodeId: String) = _queue.value.any { it.episodeId == episodeId }

    fun toggleAutoplay() {
        _autoplay.update { current ->
            val next = !current
            logger.log(LogLevel.INFO, Subsystem.QUEUE,
                LogEvent.Queue.AutoplayToggled(next))
            next
        }
    }

    fun clear() {
        _queue.update { emptyList() }
        logger.log(LogLevel.INFO, Subsystem.QUEUE, LogEvent.Queue.Cleared)
        persistQueue(emptyList())
    }

    private fun persistQueue(list: List<QueueEntry>) {
        scope.launch {
            db.transaction {
                db.queueQueries.deleteAll()
                list.forEachIndexed { index, entry ->
                    db.queueQueries.insert(
                        episodeId    = entry.episodeId,
                        position     = index.toLong(),
                        url          = entry.url,
                        title        = entry.title,
                        artworkUrl   = entry.artworkUrl,
                        podcastTitle = entry.podcastTitle,
                        durationMs   = entry.durationMs,
                    )
                }
            }
        }
    }
}
