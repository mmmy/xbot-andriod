package com.gouge.xbot.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gouge.xbot.data.ServerConfigStore
import com.gouge.xbot.data.SessionStore
import com.gouge.xbot.data.SignalViewDto
import com.gouge.xbot.data.XbotRepository
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

class SignalWidgetWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val widgetIds = manager.getAppWidgetIds(
            ComponentName(applicationContext, SignalWidgetProvider::class.java),
        )
        if (widgetIds.isEmpty()) return Result.success()

        val requestedWidgetId = inputData.getInt(
            InputAppWidgetId,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        val targetWidgetIds = if (requestedWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            widgetIds
        } else {
            if (requestedWidgetId !in widgetIds) return Result.success()
            intArrayOf(requestedWidgetId)
        }

        val preferences = WidgetPreferences(applicationContext)
        val sessionStore = SessionStore(applicationContext)
        if (sessionStore.getAccessToken().isNullOrBlank()) {
            targetWidgetIds.forEach { id ->
                SignalWidgetRenderer.render(
                    applicationContext,
                    id,
                    preferences.get(id),
                    "请登录 XBot Signal",
                )
            }
            return Result.success()
        }

        val repository = XbotRepository(ServerConfigStore(applicationContext), sessionStore)
        return try {
            if (requestedWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                refreshAllWidgets(repository, preferences, targetWidgetIds)
            } else {
                refreshOneWidget(repository, preferences, requestedWidgetId)
            }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val unauthorized = error is HttpException && error.code() == 401
            val missingSignal = error is HttpException && error.code() == 404
            if (unauthorized) sessionStore.clear()
            targetWidgetIds.forEach { id ->
                SignalWidgetRenderer.render(
                    applicationContext,
                    id,
                    preferences.get(id),
                    when {
                        unauthorized -> "登录已失效"
                        missingSignal -> "信号已不存在"
                        else -> "更新失败，保留上次数据"
                    },
                )
            }
            if (unauthorized || missingSignal) Result.success() else Result.retry()
        }
    }

    private suspend fun refreshAllWidgets(
        repository: XbotRepository,
        preferences: WidgetPreferences,
        widgetIds: IntArray,
    ) {
        val signalsById = repository.getSignalViews().associateBy { it.id }
        widgetIds.forEach { id ->
            val current = preferences.get(id)
            val signal = current?.let { signalsById[it.signalId] }
            when {
                current == null -> SignalWidgetRenderer.render(
                    applicationContext,
                    id,
                    null,
                    "请重新配置小组件",
                )
                signal == null -> SignalWidgetRenderer.render(
                    applicationContext,
                    id,
                    current,
                    "信号已不存在",
                )
                else -> saveAndRender(preferences, id, current, signal)
            }
        }
    }

    private suspend fun refreshOneWidget(
        repository: XbotRepository,
        preferences: WidgetPreferences,
        appWidgetId: Int,
    ) {
        val current = preferences.get(appWidgetId)
        if (current == null) {
            SignalWidgetRenderer.render(
                applicationContext,
                appWidgetId,
                null,
                "请重新配置小组件",
            )
            return
        }
        val signal = repository.getSignalView(current.signalId)
        saveAndRender(preferences, appWidgetId, current, signal)
    }

    private fun saveAndRender(
        preferences: WidgetPreferences,
        appWidgetId: Int,
        current: WidgetSnapshot,
        signal: SignalViewDto,
    ) {
        val updated = WidgetSnapshot.from(
            signal,
            showSymbol = current.showSymbol,
        )
        preferences.save(appWidgetId, updated)
        SignalWidgetRenderer.render(applicationContext, appWidgetId, updated)
    }

    companion object {
        const val InputAppWidgetId = "app_widget_id"
    }
}
