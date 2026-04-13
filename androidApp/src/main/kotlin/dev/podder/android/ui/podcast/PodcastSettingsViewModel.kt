package com.lush1us.podder.ui.podcast

import androidx.lifecycle.ViewModel
import dev.podder.db.PodderDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PodcastSettingsViewModel(
    private val podcastId: String,
    private val db: PodderDatabase,
) : ViewModel() {

    data class State(
        val playbackSpeed: Float? = null,
        val notificationsMuted: Boolean = false,
        val autoDownload: String = "global",
        val skipIntroS: Int = 0,
        val skipOutroS: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { load() }

    private fun load() {
        val row = db.podcastSettingsQueries.selectByPodcastId(podcastId).executeAsOneOrNull()
        if (row != null) {
            _state.value = State(
                playbackSpeed      = row.playbackSpeed?.toFloat(),
                notificationsMuted = row.notificationsMuted != 0L,
                autoDownload       = row.autoDownload,
                skipIntroS         = row.skipIntroDurationS.toInt(),
                skipOutroS         = row.skipOutroDurationS.toInt(),
            )
        }
    }

    fun save(state: State) {
        _state.value = state
        db.podcastSettingsQueries.upsert(
            podcastId          = podcastId,
            playbackSpeed      = state.playbackSpeed?.toDouble(),
            notificationsMuted = if (state.notificationsMuted) 1L else 0L,
            autoDownload       = state.autoDownload,
            skipIntroDurationS = state.skipIntroS.toLong(),
            skipOutroDurationS = state.skipOutroS.toLong(),
        )
    }
}
