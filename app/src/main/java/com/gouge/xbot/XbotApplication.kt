package com.gouge.xbot

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import com.gouge.xbot.widget.SignalWidgetProvider
import com.gouge.xbot.widget.SignalWidgetScheduler

class XbotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val widgetIds = AppWidgetManager.getInstance(this).getAppWidgetIds(
            ComponentName(this, SignalWidgetProvider::class.java),
        )
        if (widgetIds.isNotEmpty()) {
            SignalWidgetScheduler.schedulePeriodic(this)
        }
    }
}
