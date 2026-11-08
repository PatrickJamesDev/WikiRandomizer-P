package org.nsh07.wikireader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SearchHistoryItem::class,
        ViewHistoryItem::class,
        SavedArticle::class,
        SavedHighlight::class,
        VocabularyWord::class,
        UserLanguage::class,
        StringPreference::class,
        IntPreference::class,
        BooleanPreference::class
    ],
    version = 4
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun savedArticleDao(): SavedArticleDao
    abstract fun savedHighlightDao(): SavedHighlightDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun viewHistoryDao(): ViewHistoryDao
    abstract fun userLanguageDao(): UserLanguageDao
    abstract fun preferenceDao(): PreferenceDao

    companion object {

        @Volatile
        private var Instance: AppDatabase? = null

        /** v2: saved date and topic on saved/history rows, so both screens can sort by them. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_article ADD COLUMN savedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE saved_article ADD COLUMN topic TEXT")
                db.execSQL("ALTER TABLE view_history ADD COLUMN topic TEXT")
            }
        }

        /** v3: saved text highlights (article title/language + the selected passage). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_highlight` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`pageId` INTEGER, " +
                            "`lang` TEXT NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`text` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "`thumbnail` TEXT, " +
                            "`description` TEXT, " +
                            "`topic` TEXT)"
                )
            }
        }

        /** v4: vocabulary builder words saved from text selections. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vocabulary_word` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`word` TEXT NOT NULL, " +
                            "`lang` TEXT NOT NULL, " +
                            "`definition` TEXT, " +
                            "`articleTitle` TEXT, " +
                            "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_vocabulary_word_word_lang` " +
                            "ON `vocabulary_word` (`word`, `lang`)"
                )
            }
        }

        /**
         * Folds the write-ahead log into the main database file so that file alone is a complete,
         * consistent copy (needed before copying it into a backup).
         */
        fun checkpoint(context: Context) {
            getDatabase(context).openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { it.moveToFirst() }
        }

        /** The schema version of the running app's database. */
        fun currentVersion(context: Context): Int =
            getDatabase(context).openHelper.readableDatabase.version

        /** Closes the database and forgets it, so its files can be replaced (restore from backup). */
        fun closeAndReset() {
            synchronized(this) {
                runCatching { Instance?.close() }
                Instance = null
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return Instance ?: synchronized(this) {
                // Re-check inside the lock: several repositories call this from different
                // threads at startup, and without it each could build its own Room instance
                // (separate invalidation trackers, so history Flows would stop updating).
                Instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "app_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)
                    .build()
                    .also { Instance = it }
            }
        }
    }
}