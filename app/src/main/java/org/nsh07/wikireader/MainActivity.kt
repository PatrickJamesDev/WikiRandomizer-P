package org.nsh07.wikireader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nsh07.wikireader.data.AppStatus
import org.nsh07.wikireader.data.isRtl
import org.nsh07.wikireader.data.toColor
import org.nsh07.wikireader.ui.AppScreen
import org.nsh07.wikireader.ui.homeScreen.viewModel.HomeScreenViewModel
import org.nsh07.wikireader.ui.settingsScreen.viewModel.SettingsViewModel
import org.nsh07.wikireader.ui.theme.WikiRandomizerPTheme

/** Used when the user turns haptics off; existing call sites keep working unchanged. */
private object NoHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

class MainActivity : ComponentActivity() {

    val viewModel: HomeScreenViewModel by viewModels<HomeScreenViewModel>(factoryProducer = { HomeScreenViewModel.Factory })
    val settingsViewModel: SettingsViewModel by viewModels<SettingsViewModel>(factoryProducer = { SettingsViewModel.Factory })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen().setKeepOnScreenCondition { viewModel.appStatus.value != AppStatus.INITIALIZED }

        viewModel.setFilesDir(filesDir.path)
        org.nsh07.wikireader.data.PerfMonitor.attach(this)

        enableEdgeToEdge()

        setContent {
            val preferencesState by settingsViewModel.preferencesState.collectAsStateWithLifecycle()
            val appStatus by viewModel.appStatus.collectAsStateWithLifecycle()

            // Report the first genuinely drawn frame, not the moment setContent returned.
            ReportDrawnWhen { appStatus == AppStatus.INITIALIZED }

            val darkTheme = when (preferencesState.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            val seed = preferencesState.colorScheme.toColor()
            val appFont = org.nsh07.wikireader.ui.theme.rememberReadingFontFamily(preferencesState.fontStyle)
            val haptic = LocalHapticFeedback.current

            WikiRandomizerPTheme(
                darkTheme = darkTheme,
                seedColor = seed,
                blackTheme = preferencesState.blackTheme,
                reduceMotion = preferencesState.reduceMotion,
                fontFamily = appFont
            ) {
                viewModel.setCompositionLocals(
                    cs = colorScheme,
                    tg = typography
                )
                val galleryLoader = remember(viewModel) {
                    val loader: suspend (String, String) -> org.nsh07.wikireader.data.CommonsCategoryGallery? =
                        { title, lang -> viewModel.getCommonsCategoryGallery(title, lang) }
                    loader
                }
                CompositionLocalProvider(
                    org.nsh07.wikireader.ui.homeScreen.LocalCommonsGalleryLoader provides galleryLoader,
                    LocalLayoutDirection provides
                            if (isRtl(preferencesState.lang)) LayoutDirection.Rtl
                            else LayoutDirection.Ltr,
                    LocalHapticFeedback provides
                            if (preferencesState.hapticFeedback) haptic else NoHapticFeedback
                ) {
                    AppScreen(
                        viewModel = viewModel,
                        settingsViewModel = settingsViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // Lets the performance log say whether lag happens while the finger is on the screen.
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        org.nsh07.wikireader.data.PerfMonitor.onTouch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppBackgrounded()
        // Remember the open article so the next launch can reopen it.
        viewModel.saveSessionState()
    }
}
