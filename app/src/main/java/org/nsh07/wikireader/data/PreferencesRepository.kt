package org.nsh07.wikireader.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** One-shot view of every stored preference, used to keep start-up to a single query. */
data class PreferenceSnapshot(
    val strings: Map<String, String> = emptyMap(),
    val ints: Map<String, Int> = emptyMap(),
    val booleans: Map<String, Boolean> = emptyMap()
)

interface PreferencesRepository {
    suspend fun saveStringPreference(key: String, value: String): String
    suspend fun saveIntPreference(key: String, value: Int): Int
    suspend fun saveBooleanPreference(key: String, value: Boolean): Boolean

    suspend fun readStringPreference(key: String): String?
    suspend fun readIntPreference(key: String): Int?
    suspend fun readBooleanPreference(key: String): Boolean?
    suspend fun readAllPreferences(): PreferenceSnapshot

    suspend fun resetSettings()
}

class AppPreferencesRepository(
    private val ioDispatcher: CoroutineDispatcher,
    private val preferenceDao: PreferenceDao
) : PreferencesRepository {

    override suspend fun saveStringPreference(key: String, value: String) =
        withContext(ioDispatcher) {
            preferenceDao.insertStringPreference(StringPreference(key, value))
            value
        }

    override suspend fun saveIntPreference(key: String, value: Int) =
        withContext(ioDispatcher) {
            preferenceDao.insertIntPreference(IntPreference(key, value))
            value
        }

    override suspend fun saveBooleanPreference(key: String, value: Boolean) =
        withContext(ioDispatcher) {
            preferenceDao.insertBooleanPreference(BooleanPreference(key, value))
            value
        }

    override suspend fun readStringPreference(key: String): String? =
        withContext(ioDispatcher) {
            preferenceDao.getStringPreference(key)?.value
        }

    override suspend fun readIntPreference(key: String): Int? =
        withContext(ioDispatcher) {
            preferenceDao.getIntPreference(key)?.value
        }

    override suspend fun readBooleanPreference(key: String): Boolean? =
        withContext(ioDispatcher) {
            preferenceDao.getBooleanPreference(key)?.value
        }

    override suspend fun readAllPreferences(): PreferenceSnapshot =
        withContext(ioDispatcher) {
            val strings = HashMap<String, String>()
            val ints = HashMap<String, Int>()
            val booleans = HashMap<String, Boolean>()
            preferenceDao.getAllPreferences().forEach { row ->
                when (row.valueType) {
                    "string" -> row.stringValue?.let { strings[row.key] = it }
                    "int" -> row.intValue?.let { ints[row.key] = it }
                    "boolean" -> row.booleanValue?.let { booleans[row.key] = it }
                }
            }
            PreferenceSnapshot(strings, ints, booleans)
        }

    override suspend fun resetSettings() =
        withContext(ioDispatcher) {
            preferenceDao.resetStringPreferences()
            preferenceDao.resetIntPreferences()
            preferenceDao.resetBooleanPreferences()
        }

}