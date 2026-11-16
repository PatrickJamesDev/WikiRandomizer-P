package org.nsh07.wikireader.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.wikirandomizerp.app.ui.theme.WikiReaderDarkColorScheme
import com.wikirandomizerp.app.ui.theme.WikiReaderLightColorScheme

@Composable
fun WikiReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) WikiReaderDarkColorScheme else WikiReaderLightColorScheme,
        content = content
    )
}
