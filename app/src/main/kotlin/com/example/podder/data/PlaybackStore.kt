package com.example.podder.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_state")

data class SessionState(
    val guid: String,
    val position: Long,
    val isPlaying: Boolean
)

class PlaybackStore(private val context: Context) {

    companion object {
        private const val TAG = "PlaybackStore"
        private val LAST_PLAYED_GUID = stringPreferencesKey("last_played_guid")
        private val LAST_POSITION = longPreferencesKey("last_position")
        private val IS_PLAYING = booleanPreferencesKey("is_playing")
    }

    suspend fun saveState(guid: String, position: Long, isPlaying: Boolean) {
        Log.d(TAG, "Saving state: guid=$guid, position=${position}ms, isPlaying=$isPlaying")
        context.dataStore.edit { prefs ->
            prefs[LAST_PLAYED_GUID] = guid
            prefs[LAST_POSITION] = position
            prefs[IS_PLAYING] = isPlaying
        }
    }

    fun getSessionState(): Flow<SessionState?> = context.dataStore.data.map { prefs ->
        val guid = prefs[LAST_PLAYED_GUID]
        if (guid != null) {
            SessionState(
                guid = guid,
                position = prefs[LAST_POSITION] ?: 0L,
                isPlaying = prefs[IS_PLAYING] ?: false
            )
        } else {
            null
        }
    }
}
