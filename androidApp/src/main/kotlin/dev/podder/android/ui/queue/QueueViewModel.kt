package dev.podder.android.ui.queue

import androidx.lifecycle.ViewModel
import dev.podder.android.queue.QueueEntry
import dev.podder.android.queue.QueueRepository
import kotlinx.coroutines.flow.StateFlow

class QueueViewModel(
    private val queueRepository: QueueRepository,
) : ViewModel() {

    val queue: StateFlow<List<QueueEntry>> = queueRepository.queue
    val autoplay: StateFlow<Boolean> = queueRepository.autoplay

    fun addToQueue(entry: QueueEntry) = queueRepository.addToQueue(entry)
    fun removeFromQueue(episodeId: String) = queueRepository.removeFromQueue(episodeId)
    fun reorderQueue(from: Int, to: Int) = queueRepository.reorderQueue(from, to)
    fun toggleAutoplay() = queueRepository.toggleAutoplay()
    fun clear() = queueRepository.clear()
}
