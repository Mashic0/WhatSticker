package com.whatsticker.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class ColorAccent(val displayName: String, val hexColor: Long) {
    DYNAMIC("Material You (Dinámico)", 0L),
    WHATSAPP_GREEN("Verde WhatsApp", 0xFF25D366),
    OCEAN_BLUE("Azul Océano", 0xFF007AFF),
    DEEP_PURPLE("Púrpura Vibrante", 0xFF7C4DFF),
    CORAL("Coral Energético", 0xFFFF5722),
    AMBER("Ámbar Solar", 0xFFFFB300)
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorAccent: ColorAccent = ColorAccent.WHATSAPP_GREEN,
    val floatingButtonEnabled: Boolean = false
)

class PreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val COLOR_ACCENT = stringPreferencesKey("color_accent")
        val FLOATING_BUTTON_ENABLED = booleanPreferencesKey("floating_button_enabled")
    }

    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val themeModeName = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
            val themeMode = runCatching { ThemeMode.valueOf(themeModeName) }.getOrDefault(ThemeMode.SYSTEM)

            val accentName = preferences[PreferencesKeys.COLOR_ACCENT] ?: ColorAccent.WHATSAPP_GREEN.name
            val colorAccent = runCatching { ColorAccent.valueOf(accentName) }.getOrDefault(ColorAccent.WHATSAPP_GREEN)

            val floatingEnabled = preferences[PreferencesKeys.FLOATING_BUTTON_ENABLED] ?: false

            UserSettings(
                themeMode = themeMode,
                colorAccent = colorAccent,
                floatingButtonEnabled = floatingEnabled
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun setColorAccent(accent: ColorAccent) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COLOR_ACCENT] = accent.name
        }
    }

    suspend fun setFloatingButtonEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FLOATING_BUTTON_ENABLED] = enabled
        }
    }
}
