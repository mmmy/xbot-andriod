package com.gouge.xbot.data

import android.content.Context

class AlertVisibilityStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    fun resolveVisibleIds(configs: List<TvAlertConfigDto>): Set<String> {
        val currentIds = configs.mapTo(linkedSetOf()) { it.id }
        val savedIds = preferences.getStringSet(VisibleIdsKey, emptySet()).orEmpty()
        val knownIds = preferences.getStringSet(KnownIdsKey, emptySet()).orEmpty()
        val initialized = preferences.getBoolean(InitializedKey, false)
        val accountChanged = initialized && knownIds.isNotEmpty() && knownIds.intersect(currentIds).isEmpty()
        val resolved = when {
            !initialized || accountChanged -> configs.take(DefaultVisibleCount).mapTo(linkedSetOf()) { it.id }
            else -> savedIds.intersect(currentIds)
        }
        saveState(resolved, currentIds)
        return resolved
    }

    fun saveVisibleIds(ids: Set<String>, configs: List<TvAlertConfigDto>) {
        saveState(
            visibleIds = ids.intersect(configs.mapTo(linkedSetOf()) { it.id }),
            knownIds = configs.mapTo(linkedSetOf()) { it.id },
        )
    }

    private fun saveState(visibleIds: Set<String>, knownIds: Set<String>) {
        preferences.edit()
            .putBoolean(InitializedKey, true)
            .putStringSet(VisibleIdsKey, visibleIds)
            .putStringSet(KnownIdsKey, knownIds)
            .apply()
    }

    companion object {
        const val DefaultVisibleCount = 6
        private const val PreferencesName = "xbot_alert_visibility"
        private const val InitializedKey = "initialized"
        private const val VisibleIdsKey = "visible_ids"
        private const val KnownIdsKey = "known_ids"
    }
}
