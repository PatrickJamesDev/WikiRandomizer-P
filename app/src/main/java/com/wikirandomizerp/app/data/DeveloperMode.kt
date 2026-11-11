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
    private const val KEY_SUPPRESS_DONATION_PROMPTS = "suppress_donation_prompts"

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

    /**
     * Debug-only override: never show the optional milestone support prompt, even when the
     * "donation prompts" setting is on. Useful when testing the reading flow.
     */
    fun donationPromptsSuppressed(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_SUPPRESS_DONATION_PROMPTS, false)

    fun setDonationPromptsSuppressed(context: Context, suppressed: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SUPPRESS_DONATION_PROMPTS, suppressed)
            .apply()
    }
}
