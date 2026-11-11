package com.wikirandomizerp.app.data

import android.content.Context

/**
 * Local-only switch that unlocks the hidden developer menu.
 *
 * It is enabled by tapping the About screen header ten times within five seconds. The flag is a
 * plain boolean in a private preferences file: no analytics, no remote flag and no extra data.
 */
object DeveloperMode {
    private const val PREFS_FILE = "developer_mode"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun toggle(context: Context): Boolean {
        val next = !isEnabled(context)
        setEnabled(context, next)
        return next
    }
}
