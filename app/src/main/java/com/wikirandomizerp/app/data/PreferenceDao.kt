package com.wikirandomizerp.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Flat row returned by the union query so start-up does not need three queries per key. */
data class PreferenceValue(
    val key: String,
    val valueType: String,
    val stringValue: String?,
    val intValue: Int?,
    val booleanValue: Boolean?
)

@Dao
interface PreferenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStringPreference(preference: StringPreference)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntPreference(preference: IntPreference)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooleanPreference(preference: BooleanPreference)

    @Query("DELETE FROM string_preference")
    suspend fun resetStringPreferences()

    @Query("DELETE FROM int_preference")
    suspend fun resetIntPreferences()

    @Query("DELETE FROM boolean_preference")
    suspend fun resetBooleanPreferences()

    @Query("SELECT * FROM string_preference WHERE `key` = :key")
    suspend fun getStringPreference(key: String): StringPreference?

    @Query("SELECT * FROM int_preference WHERE `key` = :key")
    suspend fun getIntPreference(key: String): IntPreference?

    @Query("SELECT * FROM boolean_preference WHERE `key` = :key")
    suspend fun getBooleanPreference(key: String): BooleanPreference?

    @Query(
        """
        SELECT `key`, 'string' AS valueType, value AS stringValue,
               NULL AS intValue, NULL AS booleanValue
        FROM string_preference
        UNION ALL
        SELECT `key`, 'int' AS valueType, NULL AS stringValue,
               value AS intValue, NULL AS booleanValue
        FROM int_preference
        UNION ALL
        SELECT `key`, 'boolean' AS valueType, NULL AS stringValue,
               NULL AS intValue, value AS booleanValue
        FROM boolean_preference
        """
    )
    suspend fun getAllPreferences(): List<PreferenceValue>
}