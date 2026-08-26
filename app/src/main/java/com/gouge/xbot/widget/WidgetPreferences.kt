package com.gouge.xbot.widget

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WidgetPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )
    private val json = Json { ignoreUnknownKeys = true }

    fun save(appWidgetId: Int, snapshot: WidgetSnapshot) {
        preferences.edit().putString(key(appWidgetId), json.encodeToString(snapshot)).apply()
    }

    fun get(appWidgetId: Int): WidgetSnapshot? {
        val value = preferences.getString(key(appWidgetId), null) ?: return null
        return runCatching { json.decodeFromString<WidgetSnapshot>(value) }.getOrNull()
    }

    fun remove(appWidgetId: Int) {
        preferences.edit().remove(key(appWidgetId)).apply()
    }

    private fun key(appWidgetId: Int) = "widget_$appWidgetId"

    private companion object {
        const val PreferencesName = "signal_widget_preferences"
    }
}
