package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.lumoreDataStore: DataStore<Preferences> by preferencesDataStore(name = "lumore_preferences")

class ThemePreferencesRepository(private val context: Context) {
    private val themeModeKey = stringPreferencesKey("theme_mode")

    val themeMode: Flow<ThemeMode> = context.lumoreDataStore.data.map { prefs ->
        ThemeMode.fromRaw(prefs[themeModeKey])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.lumoreDataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }

    suspend fun toggleLightDark(currentlyDark: Boolean) {
        val next = if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK
        setThemeMode(next)
    }
}
