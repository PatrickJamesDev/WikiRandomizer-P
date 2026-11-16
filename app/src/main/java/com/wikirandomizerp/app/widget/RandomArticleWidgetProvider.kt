package com.wikirandomizerp.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.wikirandomizerp.app.MainActivity
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.data.AppLaunchExtras
import com.wikirandomizerp.app.data.DeveloperMode

/**
 * Developer-preview home-screen widget: a single button that opens a random article from the
 * user's topic library, plus a tap target that opens the app. The widget shows a short hint and
 * does nothing while developer mode (or the widget itself) is disabled.
 */
class RandomArticleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    private fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_random)
        val devEnabled = DeveloperMode.isEnabled(context)
        val enabled = devEnabled && WidgetPrefs.isEnabled(context)

        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_name))
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        if (enabled) {
            views.setTextViewText(
                R.id.widget_subtitle,
                context.getString(R.string.widget_subtitle)
            )
            views.setViewVisibility(R.id.widget_random_button, View.VISIBLE)
            views.setTextViewText(
                R.id.widget_random_button,
                context.getString(R.string.widget_button)
            )
            views.setOnClickPendingIntent(
                R.id.widget_random_button,
                randomArticle(context)
            )
        } else {
            views.setTextViewText(
                R.id.widget_subtitle,
                context.getString(
                    if (devEnabled) R.string.widget_disabled else R.string.widget_dev_only
                )
            )
            views.setViewVisibility(R.id.widget_random_button, View.GONE)
        }
        return views
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            2001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun randomArticle(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = AppLaunchExtras.ACTION_RANDOM_FROM_TOPICS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            2002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        /** Refreshes every placed widget (call it after the developer setting changes). */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context.applicationContext)
            val component = ComponentName(context.applicationContext, RandomArticleWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val provider = RandomArticleWidgetProvider()
            provider.onUpdate(context.applicationContext, manager, ids)
        }
    }
}
