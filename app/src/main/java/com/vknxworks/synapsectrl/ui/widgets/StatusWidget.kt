package com.vknxworks.synapsectrl.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.vknxworks.synapsectrl.MainActivity
import com.vknxworks.synapsectrl.R
import com.vknxworks.synapsectrl.container
import com.vknxworks.synapsectrl.data.widget.WidgetRefreshWorker
import com.vknxworks.synapsectrl.data.widget.WidgetStore
import com.vknxworks.synapsectrl.ui.components.formatCount
import com.vknxworks.synapsectrl.ui.theme.CtrlColors

/** Health colours as on the Overview, as light/dark pairs: the widget follows the device's dark mode. */
private enum class WidgetColors(val light: Int, val dark: Int) {
    OK(CtrlColors.Light.accentText.toArgb(), CtrlColors.Dark.accentText.toArgb()),
    CRITICAL(CtrlColors.Light.error.toArgb(), CtrlColors.Dark.error.toArgb()),
    IDLE(CtrlColors.Light.disabled.toArgb(), CtrlColors.Dark.disabled.toArgb()),
}

private fun RemoteViews.tintIcon(c: WidgetColors) = setColorInt(R.id.widget_icon, "setColorFilter", c.light, c.dark)

/**
 * Home screen widget for the active server: online or not, active users and open event reports. A
 * tap opens SynapseCtrl; it never changes anything. While SynapseCtrl is locked and the token is
 * sealed with the app lock, it only says that SynapseCtrl is locked.
 */
class StatusWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        draw(context, manager, ids)
        WidgetRefreshWorker.enqueue(context)
    }

    override fun onEnabled(context: Context) {
        WidgetRefreshWorker.enqueue(context)
    }

    override fun onDisabled(context: Context) {
        WidgetStore.clear(context)
    }

    companion object {
        private fun component(context: Context) = ComponentName(context, StatusWidget::class.java)

        fun anyPlaced(context: Context): Boolean =
            runCatching { AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty() }.getOrDefault(false)

        /** Redraws every placed widget from what's saved (no network). Never throws. */
        fun redraw(context: Context) {
            val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return
            val ids = runCatching { manager.getAppWidgetIds(component(context)) }.getOrNull() ?: return
            if (ids.isNotEmpty()) runCatching { draw(context, manager, ids) }
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun draw(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val c = context.container
            val views = RemoteViews(context.packageName, R.layout.widget_status)
            views.setOnClickPendingIntent(android.R.id.background, openApp(context))
            val server = c.sessions.active.value?.server
            val snap = WidgetStore.read(context)?.takeIf { it.serverId == server?.id }
            val now = System.currentTimeMillis()
            when {
                server == null -> {
                    views.tintIcon(WidgetColors.IDLE)
                    views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                    views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_no_server))
                    views.setViewVisibility(R.id.widget_stats, View.GONE)
                }
                c.serverStore.sealedNow(server.id) -> {
                    views.tintIcon(WidgetColors.IDLE)
                    views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_locked))
                    views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_locked_subtitle))
                    views.setViewVisibility(R.id.widget_stats, View.GONE)
                }
                snap == null -> {
                    views.tintIcon(WidgetColors.IDLE)
                    views.setTextViewText(R.id.widget_title, server.name)
                    views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_unknown))
                    views.setViewVisibility(R.id.widget_stats, View.GONE)
                }
                else -> {
                    views.tintIcon(if (snap.online) WidgetColors.OK else WidgetColors.CRITICAL)
                    views.setTextViewText(R.id.widget_title, server.name)
                    views.setTextViewText(
                        R.id.widget_subtitle,
                        if (snap.online) context.getString(R.string.widget_online)
                        else context.getString(R.string.widget_offline, DateUtils.getRelativeTimeSpanString(snap.updatedAt, now, DateUtils.MINUTE_IN_MILLIS)),
                    )
                    views.setViewVisibility(R.id.widget_stats, View.VISIBLE)
                    val none = context.getString(R.string.widget_no_value)
                    views.setTextViewText(R.id.widget_users, if (snap.usersActive >= 0) formatCount(snap.usersActive) else none)
                    views.setTextViewText(R.id.widget_reports, if (snap.reports >= 0) formatCount(snap.reports) else none)
                }
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
