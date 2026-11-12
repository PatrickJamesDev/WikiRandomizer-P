package com.wikirandomizerp.app.widget

import android.content.Context

/** Tiny synchronous store the widget provider can read outside the app process lifecycle. */
object WidgetPrefs {
    private const val PREFS = "app_widgets"
    private const val KEY_ENABLED = "home_widget_enabled"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
