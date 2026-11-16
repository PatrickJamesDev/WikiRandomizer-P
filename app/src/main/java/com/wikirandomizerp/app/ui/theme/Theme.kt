package com.wikirandomizerp.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WikiRandomizerPTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    seedColor: Color = Color.White,
    dynamicColor: Boolean = true,
    blackTheme: Boolean = false,
    reduceMotion: Boolean = false,
    /** Font chosen in Settings; applied to every text style of the app. */
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> WikiReaderDarkColorScheme
        else -> WikiReaderLightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Wrapped contexts are not an Activity; skipping the status-bar tint beats crashing the app
            var ctx = view.context
            while (ctx is android.content.ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
            (ctx as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }
    CustomColors.black = blackTheme && darkTheme

    // Generating a MaterialKolor scheme is one of the most expensive things in the first frame.
    // The default ("follow the system seed") uses the platform scheme directly, so only pay for
    // the generator when a custom seed or the black theme actually needs it.
    val scheme =
        if (seedColor == Color.White && !(blackTheme && darkTheme)) colorScheme
        else rememberDynamicColorScheme(
            seedColor = when (seedColor) {
                Color.White -> colorScheme.primary
                else -> seedColor
            },
            isDark = darkTheme,
            specVersion = if (blackTheme && darkTheme) ColorSpec.SpecVersion.SPEC_2021 else ColorSpec.SpecVersion.SPEC_2025,
            isAmoled = blackTheme && darkTheme
        )

    MaterialExpressiveTheme(
        colorScheme = scheme,
        typography = remember(fontFamily) { typographyWithFont(fontFamily) },
        // Reduce-motion still keeps every transition, just on the quieter standard spec.
        motionScheme = if (reduceMotion) MotionScheme.standard() else MotionScheme.expressive(),
        content = content
    )
}

@Composable
fun ColorScheme.isDark() = this.background.luminance() < 0.5
