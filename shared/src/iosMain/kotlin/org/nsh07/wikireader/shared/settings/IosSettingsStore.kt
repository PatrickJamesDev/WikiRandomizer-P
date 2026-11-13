package org.nsh07.wikireader.shared.settings

import platform.Foundation.NSUserDefaults

class IosSettingsStore : SettingsStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String, defaultValue: String): String =
        defaults.stringForKey(key) ?: defaultValue

    override fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }
}
