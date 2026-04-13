package com.lush1us.podder.ui.settings

import androidx.lifecycle.ViewModel
import dev.podder.data.repository.PodcastRepository
import dev.podder.data.store.KVStore
import dev.podder.domain.parser.generateOpml
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class SettingsViewModel(
    private val kvStore: KVStore,
    private val podcastRepository: PodcastRepository,
) : ViewModel() {

    private val _refreshIntervalHours = MutableStateFlow(kvStore.getLong("refresh_interval_hours", 3L))
    val refreshIntervalHours: StateFlow<Long> = _refreshIntervalHours.asStateFlow()

    private val _defaultSpeed = MutableStateFlow(kvStore.getFloat("default_playback_speed", 1.0f))
    val defaultSpeed: StateFlow<Float> = _defaultSpeed.asStateFlow()

    private val _wifiOnly = MutableStateFlow(kvStore.getBool("download_wifi_only", false))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _storageCap = MutableStateFlow(kvStore.getLong("storage_cap_mb", 5120L))
    val storageCap: StateFlow<Long> = _storageCap.asStateFlow()

    private val _unsubBehavior = MutableStateFlow(kvStore.getString("unsub_download_behavior", "ask"))
    val unsubBehavior: StateFlow<String> = _unsubBehavior.asStateFlow()

    fun setRefreshInterval(hours: Long) { _refreshIntervalHours.value = hours; kvStore.putLong("refresh_interval_hours", hours) }
    fun setDefaultSpeed(speed: Float)   { _defaultSpeed.value = speed;   kvStore.putFloat("default_playback_speed", speed) }
    fun setWifiOnly(enabled: Boolean)   { _wifiOnly.value = enabled;     kvStore.putBool("download_wifi_only", enabled) }
    fun setStorageCap(mb: Long)         { _storageCap.value = mb;        kvStore.putLong("storage_cap_mb", mb) }
    fun setUnsubBehavior(behavior: String) { _unsubBehavior.value = behavior; kvStore.putString("unsub_download_behavior", behavior) }

    suspend fun generateOpml(): String {
        val podcasts = podcastRepository.podcasts().first()
        return generateOpml(podcasts)
    }
}
