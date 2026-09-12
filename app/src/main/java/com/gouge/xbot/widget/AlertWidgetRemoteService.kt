package com.gouge.xbot.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.gouge.xbot.data.AlertVisibilityStore
import java.time.Instant

// Compatibility adapter for Android 8–11. Android 12+ uses RemoteCollectionItems.
class AlertWidgetRemoteService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = object : RemoteViewsFactory {
        private val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        private var rows = emptyList<AlertWidgetRow>()
        private var density = AlertWidgetDensity.Compact
        override fun onCreate() = onDataSetChanged()
        override fun onDataSetChanged() {
            val store = AlertWidgetStore(applicationContext)
            val settings = store.settings(id)
            density = settings.density
            rows = alertWidgetRows(store.snapshot() ?: AlertSnapshot(), AlertVisibilityStore(applicationContext).getVisibleIds(), settings)
        }
        override fun getCount() = rows.size
        override fun getViewAt(position: Int): RemoteViews? = rows.getOrNull(position)?.let {
            AlertWidgetRenderer.rowViews(applicationContext, it, density, Instant.now())
        }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 3
        override fun getItemId(position: Int) = rows.getOrNull(position)?.let { AlertWidgetRenderer.stableId(it.key) } ?: 0L
        override fun hasStableIds() = true
        override fun onDestroy() { rows = emptyList() }
    }
}
