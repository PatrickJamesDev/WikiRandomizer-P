package com.wikirandomizerp.app.ui.theme

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.wikirandomizerp.app.R
import java.io.File

/**
 * The font used for article text. Most choices are bundled (no network needed) and the user can
 * also import any TTF/OTF file from the device; that file is copied into app-private storage so it
 * keeps working after the source document is removed.
 */
@Composable
fun rememberReadingFontFamily(fontStyle: String): FontFamily {
    val context = LocalContext.current
    return remember(fontStyle) {
        when (fontStyle) {
            "sans" -> FontFamily.SansSerif
            "serif" -> FontFamily.Serif
            "mono" -> FontFamily.Monospace
            "atkinson" -> FontFamily(Font(R.font.atkinson_hyperlegible))
            "lora" -> variableFamily(R.font.lora)
            "inter" -> variableFamily(R.font.inter)
            "source_serif" -> variableFamily(R.font.source_serif_4)
            "literata" -> variableFamily(R.font.literata)
            "ibm_plex" -> FontFamily(Font(R.font.ibm_plex_serif))
            "open_sans" -> variableFamily(R.font.open_sans)
            "custom" -> runCatching {
                val file = File(File(context.filesDir, "fonts"), "custom.ttf")
                require(file.isFile && file.length() > 0)
                FontFamily(Typeface.createFromFile(file))
            }.getOrDefault(FontFamily.Serif)
            else -> FontFamily.Serif
        }
    }
}

/**
 * Most bundled fonts are variable fonts. Declaring the weights explicitly lets headings and bold
 * text use real Medium/SemiBold/Bold glyphs now that the font also drives menus and titles,
 * instead of a single weight with faked bold.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int): FontFamily = FontFamily(
    listOf(
        FontWeight.Normal to 400,
        FontWeight.Medium to 500,
        FontWeight.SemiBold to 600,
        FontWeight.Bold to 700
    ).map { (weight, value) ->
        Font(
            resId,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(value))
        )
    }
)

/** Human-readable names for every selectable font, in picker order. */
@Composable
fun readingFontLabels(): List<Pair<String, String>> = listOf(
    "sans" to stringResource(R.string.fontSystemSans),
    "serif" to stringResource(R.string.fontSystemSerif),
    "mono" to stringResource(R.string.fontMonospace),
    "atkinson" to stringResource(R.string.fontAtkinson),
    "lora" to "Lora",
    "inter" to "Inter",
    "source_serif" to "Source Serif 4",
    "literata" to "Literata",
    "ibm_plex" to "IBM Plex Serif",
    "open_sans" to "Open Sans",
    "custom" to stringResource(R.string.fontImported)
)
