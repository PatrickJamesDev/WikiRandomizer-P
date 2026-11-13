package com.wikirandomizerp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import org.nsh07.wikireader.shared.reader.AndroidRandomArticleSource
import org.nsh07.wikireader.shared.reader.AndroidWikipediaSearchSource
import org.nsh07.wikireader.shared.settings.AndroidSettingsStore
import org.nsh07.wikireader.shared.ui.ReaderApp

/**
 * Developer-only preview of the shared Compose shell on Android.
 *
 * This is the migration seam: the production launcher still opens the feature-complete Android
 * UI, while this activity renders exactly the same [ReaderApp] as iOS. Once the remaining Android
 * features move into `shared`, this activity can become the main entry point.
 */
class SharedReaderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val source = remember { AndroidRandomArticleSource() }
            val searchSource = remember { AndroidWikipediaSearchSource() }
            val settingsStore = remember { AndroidSettingsStore(this) }
            ReaderApp(
                source = source,
                searchSource = searchSource,
                settingsStore = settingsStore
            )
        }
    }
}
