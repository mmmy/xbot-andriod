package com.gouge.xbot.widget

import android.content.Context
import com.gouge.xbot.data.ServerConfigStore
import com.gouge.xbot.data.SessionStore
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class AlertSnapshotEnvelope(val scope: String, val snapshot: AlertSnapshot)

class AlertWidgetStore(context: Context) {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("alert_widget_data", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun currentScope(): String? {
        val token = SessionStore(context).getAccessToken() ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((ServerConfigStore(context).getBaseUrl() + "\n" + token).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun snapshot(): AlertSnapshot? {
        val scope = currentScope() ?: return null
        val value = preferences.getString("snapshot", null) ?: return null
        return runCatching { json.decodeFromString<AlertSnapshotEnvelope>(value) }.getOrNull()
            ?.takeIf { it.scope == scope }?.snapshot
    }

    fun saveSnapshot(scope: String, snapshot: AlertSnapshot) {
        if (scope != currentScope()) return
        preferences.edit().putString("snapshot", json.encodeToString(AlertSnapshotEnvelope(scope, snapshot))).apply()
    }

    fun clearSnapshot() { preferences.edit().remove("snapshot").apply() }

    fun settings(id: Int): AlertWidgetSettings = preferences.getString("settings_$id", null)?.let {
        runCatching { json.decodeFromString<AlertWidgetSettings>(it) }.getOrNull()
    } ?: AlertWidgetSettings()

    fun saveSettings(id: Int, settings: AlertWidgetSettings) {
        preferences.edit().putString("settings_$id", json.encodeToString(settings)).apply()
    }

    fun remove(id: Int) { preferences.edit().remove("settings_$id").apply() }
}
