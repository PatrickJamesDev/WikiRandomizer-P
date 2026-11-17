package com.wikirandomizerp.app.widget

import android.content.Context

/** Tiny synchronous store the widget provider can read outside the app process lifecycle. */
object WidgetPrefs {
    private const val PREFS = "app_widgets"
    private const val KEY_ENABLED = "home_widget_enabled"
    private const val KEY_PREMIUM = "premium_unlocked"

    /**
     * Mirror of the app's premium state (the `supporter-mode` setting lives in the Room database,
     * which a widget provider must not open from a broadcast). Kept in sync by SettingsViewModel.
     */
    fun isPremium(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREMIUM, false)

    fun setPremium(context: Context, premium: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREMIUM, premium)
            .apply()
    }

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
