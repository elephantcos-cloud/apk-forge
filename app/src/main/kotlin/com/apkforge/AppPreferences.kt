package com.apkforge

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "apk_forge_prefs")

/**
 * APK Forge — App Preferences (Kotlin DataStore)
 */
object AppPreferences {

    private lateinit var ctx: Context

    fun init(context: Context) {
        ctx = context.applicationContext
    }

    // ── Keys ───────────────────────────────────────────────────────────────
    private val KEY_GITHUB_TOKEN   = stringPreferencesKey("github_token")
    private val KEY_GITHUB_USER    = stringPreferencesKey("github_user")
    private val KEY_DEFAULT_REPO   = stringPreferencesKey("default_repo")
    private val KEY_DEFAULT_BRANCH = stringPreferencesKey("default_branch")
    private val KEY_WEBHOOK_URL    = stringPreferencesKey("webhook_url")
    private val KEY_AUTO_INSTALL   = booleanPreferencesKey("auto_install")
    private val KEY_DARK_MODE      = booleanPreferencesKey("dark_mode")
    private val KEY_POLL_INTERVAL  = intPreferencesKey("poll_interval")
    private val KEY_SETUP_DONE     = booleanPreferencesKey("setup_done")

    // ── Flows ──────────────────────────────────────────────────────────────
    val githubToken: Flow<String> get() = ctx.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_GITHUB_TOKEN] ?: "" }

    val githubUser: Flow<String> get() = ctx.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_GITHUB_USER] ?: "" }

    val defaultRepo: Flow<String> get() = ctx.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DEFAULT_REPO] ?: "" }

    val defaultBranch: Flow<String> get() = ctx.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DEFAULT_BRANCH] ?: "main" }

    val webhookUrl: Flow<String> get() = ctx.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_WEBHOOK_URL] ?: "" }

    val autoInstall: Flow<Boolean> get() = ctx.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_AUTO_INSTALL] ?: false }

    val darkMode: Flow<Boolean> get() = ctx.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DARK_MODE] ?: true }

    val pollInterval: Flow<Int> get() = ctx.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_POLL_INTERVAL] ?: 8 }

    val isSetupDone: Flow<Boolean> get() = ctx.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_SETUP_DONE] ?: false }

    // ── Setters ────────────────────────────────────────────────────────────
    suspend fun setGithubToken(token: String) = ctx.dataStore.edit { it[KEY_GITHUB_TOKEN] = token }
    suspend fun setGithubUser(user: String)   = ctx.dataStore.edit { it[KEY_GITHUB_USER] = user }
    suspend fun setDefaultRepo(repo: String)  = ctx.dataStore.edit { it[KEY_DEFAULT_REPO] = repo }
    suspend fun setDefaultBranch(b: String)   = ctx.dataStore.edit { it[KEY_DEFAULT_BRANCH] = b }
    suspend fun setWebhookUrl(url: String)    = ctx.dataStore.edit { it[KEY_WEBHOOK_URL] = url }
    suspend fun setAutoInstall(v: Boolean)    = ctx.dataStore.edit { it[KEY_AUTO_INSTALL] = v }
    suspend fun setDarkMode(v: Boolean)       = ctx.dataStore.edit { it[KEY_DARK_MODE] = v }
    suspend fun setPollInterval(v: Int)       = ctx.dataStore.edit { it[KEY_POLL_INTERVAL] = v }
    suspend fun setSetupDone(v: Boolean)      = ctx.dataStore.edit { it[KEY_SETUP_DONE] = v }

    suspend fun clearAll() = ctx.dataStore.edit { it.clear() }
}
