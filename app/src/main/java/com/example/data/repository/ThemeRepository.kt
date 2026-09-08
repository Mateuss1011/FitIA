package com.example.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fitai_theme_settings")

enum class AppThemeMode {
    DARK, LIGHT, SYSTEM
}

class ThemeRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("app_theme_mode")
        val HAS_SEEN_TOUR = booleanPreferencesKey("has_seen_app_tour")
    }

    val themeModeFlow: Flow<AppThemeMode> = context.dataStore.data
        .catch { exception ->
            Log.e("FitAI_Error", "Error reading themeMode from DataStore: ${exception.message}", exception)
            emit(emptyPreferences())
        }
        .map { preferences ->
            val modeString = preferences[PreferencesKeys.THEME_MODE] ?: AppThemeMode.DARK.name
            try {
                AppThemeMode.valueOf(modeString)
            } catch (e: Exception) {
                Log.e("FitAI_Error", "Error parsing AppThemeMode '$modeString': ${e.message}", e)
                AppThemeMode.DARK
            }
        }

    val hasSeenTourFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            Log.e("FitAI_Error", "Error reading hasSeenTour from DataStore: ${exception.message}", exception)
            emit(emptyPreferences())
        }
        .map { preferences ->
            preferences[PreferencesKeys.HAS_SEEN_TOUR] ?: false
        }

    suspend fun setThemeMode(mode: AppThemeMode) {
        try {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.THEME_MODE] = mode.name
            }
        } catch (e: Exception) {
            Log.e("FitAI_Error", "Error writing theme mode to DataStore: ${e.message}", e)
        }
    }

    suspend fun setHasSeenTour(hasSeen: Boolean) {
        try {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.HAS_SEEN_TOUR] = hasSeen
            }
        } catch (e: Exception) {
            Log.e("FitAI_Error", "Error writing hasSeenTour to DataStore: ${e.message}", e)
        }
    }

    suspend fun clearAllPreferences() {
        try {
            context.dataStore.edit { preferences ->
                preferences.clear()
            }
        } catch (e: Exception) {
            Log.e("FitAI_Error", "Error clearing theme DataStore: ${e.message}", e)
        }
    }
}
