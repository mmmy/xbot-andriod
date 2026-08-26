package com.gouge.xbot.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.gouge.xbot.MainActivity
import com.gouge.xbot.R
import com.gouge.xbot.domain.DirectionState
import com.gouge.xbot.domain.SignalCommentItem
import com.gouge.xbot.domain.SignalCommentType
import com.gouge.xbot.domain.directionState
import com.gouge.xbot.domain.formatWidgetExpiry
import com.gouge.xbot.domain.levelText
import com.gouge.xbot.domain.parseSignalComment
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object SignalWidgetRenderer {
    fun render(
        context: Context,
        appWidgetId: Int,
        snapshot: WidgetSnapshot?,
        status: String? = null,
    ) {
        val views = RemoteViews(context.packageName, R.layout.signal_widget)
        val signal = snapshot?.toSignalViewDto()
        val comments = parseSignalComment(signal?.comment)

        if (signal == null) {
            views.setViewVisibility(R.id.widget_signal_icon, View.GONE)
            views.setViewVisibility(R.id.widget_symbol, View.VISIBLE)
            views.setTextViewText(R.id.widget_symbol, "XBot Signal")
            views.setTextViewText(R.id.widget_name, "尚未选择信号")
            views.setTextViewText(R.id.widget_level, "级别  -")
            views.setTextViewText(R.id.widget_direction, "-")
            views.setTextViewText(R.id.widget_expiry, "-")
        } else {
            val direction = directionState(signal.longOn, signal.shortOn)
            val expiry = formatWidgetExpiry(signal.expireAt)
            val iconType = resolveSignalIcon(
                signal.name,
                SignalIconMappingStore(context).getAll(),
            )
            views.setViewVisibility(
                R.id.widget_signal_icon,
                if (iconType == null) View.GONE else View.VISIBLE,
            )
            if (iconType != null) {
                views.setImageViewResource(
                    R.id.widget_signal_icon,
                    iconType.drawableResource(direction),
                )
                views.setContentDescription(
                    R.id.widget_signal_icon,
                    "${signal.name} · ${iconType.label} · ${direction.label}",
                )
            }
            views.setViewVisibility(
                R.id.widget_symbol,
                if (snapshot.showSymbol) View.VISIBLE else View.GONE,
            )
            views.setTextViewText(R.id.widget_symbol, signal.symbol.ifBlank { "-" })
            views.setTextViewText(
                R.id.widget_name,
                if (iconType == null) signal.name.ifBlank { "信号设置" } else "",
            )
            views.setTextViewText(R.id.widget_level, "级别  ${signal.levelText()}  ›")
            views.setTextViewText(R.id.widget_direction, direction.label)
            views.setTextViewText(R.id.widget_expiry, "${expiry.text}  ›")
            views.setTextColor(
                R.id.widget_direction,
                context.getColor(direction.colorResource()),
            )
            views.setTextColor(
                R.id.widget_expiry,
                context.getColor(
                    when {
                        expiry.isExpired -> R.color.widget_expired
                        expiry.isExpiringSoon -> R.color.signal_orange
                        else -> R.color.signal_cyan
                    },
                ),
            )
        }
        renderComments(
            views = views,
            comments = comments,
        )

        val footer = status ?: snapshot?.let {
            val updated = DateTimeFormatter.ofPattern("HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(it.updatedAtMillis))
            updated
        } ?: "点击进入应用"
        views.setTextViewText(R.id.widget_status, footer)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context,
                appWidgetId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        if (snapshot != null) {
            views.setOnClickPendingIntent(
                R.id.widget_level,
                quickSettingsPendingIntent(context, appWidgetId, QuickSettingsField.Level),
            )
            views.setOnClickPendingIntent(
                R.id.widget_expiry,
                quickSettingsPendingIntent(context, appWidgetId, QuickSettingsField.Expiry),
            )
        }

        val settingsIntent = Intent(context, SignalWidgetConfigActivity::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("xbot-widget://settings/$appWidgetId")
        }
        views.setOnClickPendingIntent(
            R.id.widget_settings,
            PendingIntent.getActivity(
                context,
                appWidgetId,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        val refreshIntent = Intent(context, SignalWidgetProvider::class.java).apply {
            action = SignalWidgetProvider.ActionRefresh
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("xbot-widget://refresh/$appWidgetId")
        }
        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
    }

    fun renderAll(context: Context) {
        val widgetIds = AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, SignalWidgetProvider::class.java),
        )
        val preferences = WidgetPreferences(context)
        widgetIds.forEach { appWidgetId ->
            render(context, appWidgetId, preferences.get(appWidgetId))
        }
    }

    private fun quickSettingsPendingIntent(
        context: Context,
        appWidgetId: Int,
        field: QuickSettingsField,
    ): PendingIntent {
        val intent = Intent(context, WidgetQuickSettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(WidgetQuickSettingsActivity.ExtraField, field.intentValue)
            data = Uri.parse("xbot-widget://quick/$appWidgetId/${field.intentValue}")
        }
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun DirectionState.colorResource(): Int = when (this) {
        DirectionState.LongOnly -> R.color.signal_green
        DirectionState.ShortOnly -> R.color.signal_red
        DirectionState.Both -> R.color.signal_purple
        DirectionState.Disabled -> R.color.widget_muted
    }

    private fun renderComments(
        views: RemoteViews,
        comments: List<SignalCommentItem>,
    ) {
        val commentViewIds = listOf(
            R.id.widget_comment_plain,
            R.id.widget_open_long,
            R.id.widget_open_short,
            R.id.widget_close_long,
            R.id.widget_close_short,
        )
        commentViewIds.forEach { views.setViewVisibility(it, View.GONE) }

        if (comments.isEmpty()) {
            views.setViewVisibility(R.id.widget_comment_container, View.GONE)
            return
        }

        views.setViewVisibility(R.id.widget_comment_container, View.VISIBLE)
        val plainComment = comments.singleOrNull { it.type == null }
        if (plainComment != null) {
            views.setTextViewText(R.id.widget_comment_plain, plainComment.text)
            views.setViewVisibility(R.id.widget_comment_plain, View.VISIBLE)
            return
        }

        val commentsByType = comments.associateBy { it.type }
        setActionComment(views, R.id.widget_open_long, commentsByType[SignalCommentType.OpenLong])
        setActionComment(views, R.id.widget_open_short, commentsByType[SignalCommentType.OpenShort])
        setActionComment(views, R.id.widget_close_long, commentsByType[SignalCommentType.CloseLong])
        setActionComment(views, R.id.widget_close_short, commentsByType[SignalCommentType.CloseShort])
    }

    private fun setActionComment(
        views: RemoteViews,
        viewId: Int,
        comment: SignalCommentItem?,
    ) {
        if (comment == null) return
        val label = comment.type?.label.orEmpty()
        val text = if (comment.text.isEmpty()) label else "$label  ${comment.text}"
        views.setTextViewText(viewId, text)
        views.setViewVisibility(viewId, View.VISIBLE)
    }
}
