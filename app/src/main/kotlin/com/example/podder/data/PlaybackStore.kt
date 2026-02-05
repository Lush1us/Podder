package com.example.podder.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_state")

class PlaybackStore(private val context: Context) {

    companion object {
        private const val TAG = "PlaybackStore"
        private val LAST_PLAYED_GUID = stringPreferencesKey("last_played_guid")
        private val LAST_PLAYED_URL = stringPreferencesKey("last_played_url")
    }

    suspend fun saveLastPlayed(guid: String, audioUrl: String?) {
        Log.d(TAG, "Saving last played: guid=$guid, url=$audioUrl")
        context.dataStore.edit { prefs ->
            prefs[LAST_PLAYED_GUID] = guid
            if (audioUrl != null) {
                prefs[LAST_PLAYED_URL] = audioUrl
            } else {
                prefs.remove(LAST_PLAYED_URL)
            }
        }
        Log.d(TAG, "Saved successfully")
    }

    val lastPlayed: Flow<Pair<String, String?>?> = context.dataStore.data.map { prefs ->
        val guid = prefs[LAST_PLAYED_GUID]
        Log.d(TAG, "Reading last played: guid=$guid")
        if (guid != null) {
            Pair(guid, prefs[LAST_PLAYED_URL])
        } else {
            null
        }
    }
}
