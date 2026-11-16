package com.wikirandomizerp.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.wikirandomizerp.app.R

/**
 * Reading themes available for article pages. The default theme follows the app's Material
 * colours; every other theme overrides the page background and ink so long reads stay
 * comfortable. All non-default themes are premium.
 */
enum class ReadingTheme(
    val id: String,
    val background: Color,
    val ink: Color,
    val link: Color,
    val premium: Boolean,
    val labelRes: Int,
    val descriptionRes: Int
) {
    DEFAULT(
        "default",
        Color.Unspecified,
        Color.Unspecified,
        Color.Unspecified,
        premium = false,
        R.string.readingThemeDefault,
        R.string.readingThemeDefaultDesc
    ),
    SEPIA(
        "sepia",
        Color(0xFFF4ECD8),
        Color(0xFF5B4636),
        Color(0xFF7A4E16),
        premium = true,
        R.string.readingThemeSepia,
        R.string.readingThemeSepiaDesc
    ),
    SOLARIZED_LIGHT(
        "solarized_light",
        Color(0xFFFDF6E3),
        Color(0xFF586E75),
        Color(0xFF268BD2),
        premium = true,
        R.string.readingThemeSolarizedLight,
        R.string.readingThemeSolarizedLightDesc
    ),
    SOLARIZED_DARK(
        "solarized_dark",
        Color(0xFF002B36),
        Color(0xFF93A1A1),
        Color(0xFF2AA198),
        premium = true,
        R.string.readingThemeSolarizedDark,
        R.string.readingThemeSolarizedDarkDesc
    ),
    NORD(
        "nord",
        Color(0xFF2E3440),
        Color(0xFFD8DEE9),
        Color(0xFF88C0D0),
        premium = true,
        R.string.readingThemeNord,
        R.string.readingThemeNordDesc
    ),
    GRUVBOX(
        "gruvbox",
        Color(0xFF282828),
        Color(0xFFEBDBB2),
        Color(0xFFFABD2F),
        premium = true,
        R.string.readingThemeGruvbox,
        R.string.readingThemeGruvboxDesc
    ),
    PAPER(
        "paper",
        Color(0xFFFAF4E8),
        Color(0xFF3E3228),
        Color(0xFF8B5E3C),
        premium = true,
        R.string.readingThemePaper,
        R.string.readingThemePaperDesc
    ),
    GRAY(
        "gray",
        Color(0xFFE8E8E8),
        Color(0xFF2E2E2E),
        Color(0xFF3B6EA5),
        premium = true,
        R.string.readingThemeGray,
        R.string.readingThemeGrayDesc
    );

    companion object {
        fun fromId(id: String?): ReadingTheme =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
