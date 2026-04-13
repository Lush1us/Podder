package com.lush1us.podder.ui.queue

import androidx.lifecycle.ViewModel
import com.lush1us.podder.queue.QueueEntry
import com.lush1us.podder.queue.QueueRepository
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
