package com.kuromify.kuromix.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "kuromix_prefs")

@Serializable
data class AppConfig(
    val dpi: Int = 320,
    val lensOffset: Int = 450,
    val enabled: Boolean = true
)

class WhitelistManager(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private val WHITELIST_KEY = stringSetPreferencesKey("mirrored_apps_whitelist")
        private val ENFORCE_WHITELIST_KEY = booleanPreferencesKey("enforce_whitelist")
        private val DARK_MODE_PREF_KEY = intPreferencesKey("dark_mode_preference")
        private val LENS_OPTIMIZATION_KEY = booleanPreferencesKey("lens_optimization_enabled")
        private val LENS_OFFSET_KEY = intPreferencesKey("lens_offset_pixels")
        private val DPI_KEY = intPreferencesKey("rear_display_dpi")
        private val MODULE_MIRROR_ENABLED_KEY = booleanPreferencesKey("module_mirror_enabled")
        private val MODULE_KEEP_AWAKE_ENABLED_KEY = booleanPreferencesKey("module_keep_awake_enabled")
        private val MODULE_ANTI_KILL_ENABLED_KEY = booleanPreferencesKey("module_anti_kill_enabled")
        private val MODULE_REPLACE_MIPAY_KEY = booleanPreferencesKey("module_replace_mipay_enabled")
        private val PER_APP_CONFIG_KEY = stringPreferencesKey("per_app_config_json")
    }

    val whitelistFlow: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[WHITELIST_KEY] ?: emptySet()
        }

    val enforceWhitelistFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[ENFORCE_WHITELIST_KEY] ?: false
        }

    val darkModePrefFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[DARK_MODE_PREF_KEY] ?: 0 // 0: System, 1: Light, 2: Dark
        }

    val lensOptimizationFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[LENS_OPTIMIZATION_KEY] ?: false
        }

    val lensOffsetFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[LENS_OFFSET_KEY] ?: 450
        }

    val dpiFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[DPI_KEY] ?: 320
        }

    val mirrorModuleEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[MODULE_MIRROR_ENABLED_KEY] ?: false
        }

    val keepAwakeEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[MODULE_KEEP_AWAKE_ENABLED_KEY] ?: false
        }

    val antiKillEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[MODULE_ANTI_KILL_ENABLED_KEY] ?: false
        }

    val replaceMipayEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[MODULE_REPLACE_MIPAY_KEY] ?: false
        }

    val perAppConfigFlow: Flow<Map<String, AppConfig>> = context.dataStore.data
        .map { preferences ->
            val jsonStr = preferences[PER_APP_CONFIG_KEY] ?: return@map emptyMap<String, AppConfig>()
            try {
                json.decodeFromString<Map<String, AppConfig>>(jsonStr)
            } catch (_: Exception) {
                emptyMap()
            }
        }

    suspend fun setMirrorModuleEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MODULE_MIRROR_ENABLED_KEY] = enabled
            preferences[MODULE_KEEP_AWAKE_ENABLED_KEY] = enabled
            preferences[MODULE_ANTI_KILL_ENABLED_KEY] = enabled
            preferences[LENS_OPTIMIZATION_KEY] = enabled
        }
    }

    suspend fun setKeepAwakeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MODULE_KEEP_AWAKE_ENABLED_KEY] = enabled
        }
    }

    suspend fun setAntiKillEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MODULE_ANTI_KILL_ENABLED_KEY] = enabled
        }
    }

    suspend fun setReplaceMipayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MODULE_REPLACE_MIPAY_KEY] = enabled
        }
    }

    suspend fun setEnforceWhitelist(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENFORCE_WHITELIST_KEY] = enabled
        }
    }

    suspend fun setDarkModePref(pref: Int) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE_PREF_KEY] = pref
        }
    }

    suspend fun setLensOptimization(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LENS_OPTIMIZATION_KEY] = enabled
        }
    }

    suspend fun setLensOffset(offset: Int) {
        context.dataStore.edit { preferences ->
            preferences[LENS_OFFSET_KEY] = offset
        }
    }

    suspend fun setDpi(dpi: Int) {
        context.dataStore.edit { preferences ->
            preferences[DPI_KEY] = dpi
        }
    }

    suspend fun updateAppConfig(packageName: String, config: AppConfig) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[PER_APP_CONFIG_KEY] ?: "{}"
            val currentMap = try {
                json.decodeFromString<Map<String, AppConfig>>(currentJson).toMutableMap()
            } catch (e: Exception) {
                mutableMapOf<String, AppConfig>()
            }
            currentMap[packageName] = config
            preferences[PER_APP_CONFIG_KEY] = json.encodeToString(currentMap)
        }
    }

    suspend fun updateBatchAppConfig(packageNames: List<String>, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[PER_APP_CONFIG_KEY] ?: "{}"
            val currentMap = try {
                json.decodeFromString<Map<String, AppConfig>>(currentJson).toMutableMap()
            } catch (e: Exception) {
                mutableMapOf<String, AppConfig>()
            }
            packageNames.forEach { pkg ->
                val current = currentMap[pkg] ?: AppConfig()
                currentMap[pkg] = current.copy(enabled = enabled)
            }
            preferences[PER_APP_CONFIG_KEY] = json.encodeToString(currentMap)
        }
    }

    suspend fun getAppConfig(packageName: String): AppConfig {
        val prefs = context.dataStore.data.first()
        val jsonStr = prefs[PER_APP_CONFIG_KEY] ?: return AppConfig()
        return try {
            val map = json.decodeFromString<Map<String, AppConfig>>(jsonStr)
            map[packageName] ?: AppConfig()
        } catch (e: Exception) {
            AppConfig()
        }
    }

    suspend fun addToWhitelist(packageName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[WHITELIST_KEY] ?: emptySet()
            preferences[WHITELIST_KEY] = current + packageName
        }
    }

    suspend fun removeFromWhitelist(packageName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[WHITELIST_KEY] ?: emptySet()
            preferences[WHITELIST_KEY] = current - packageName
        }
    }

    suspend fun isWhitelisted(packageName: String): Boolean {
        val current = context.dataStore.data.first()[WHITELIST_KEY] ?: emptySet()
        return current.contains(packageName)
    }
}