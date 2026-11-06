package org.nsh07.wikireader.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = androidx.compose.material3.Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * The app typography with [family] applied to every text style, so the font picked in Settings
 * changes all text in the app (menus, dialogs, lists, buttons), not just the article body.
 * `null` keeps the system default font.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun typographyWithFont(family: FontFamily?): androidx.compose.material3.Typography {
    val base = Typography
    if (family == null) return base
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
        displayLargeEmphasized = base.displayLargeEmphasized.copy(fontFamily = family),
        displayMediumEmphasized = base.displayMediumEmphasized.copy(fontFamily = family),
        displaySmallEmphasized = base.displaySmallEmphasized.copy(fontFamily = family),
        headlineLargeEmphasized = base.headlineLargeEmphasized.copy(fontFamily = family),
        headlineMediumEmphasized = base.headlineMediumEmphasized.copy(fontFamily = family),
        headlineSmallEmphasized = base.headlineSmallEmphasized.copy(fontFamily = family),
        titleLargeEmphasized = base.titleLargeEmphasized.copy(fontFamily = family),
        titleMediumEmphasized = base.titleMediumEmphasized.copy(fontFamily = family),
        titleSmallEmphasized = base.titleSmallEmphasized.copy(fontFamily = family),
        bodyLargeEmphasized = base.bodyLargeEmphasized.copy(fontFamily = family),
        bodyMediumEmphasized = base.bodyMediumEmphasized.copy(fontFamily = family),
        bodySmallEmphasized = base.bodySmallEmphasized.copy(fontFamily = family),
        labelLargeEmphasized = base.labelLargeEmphasized.copy(fontFamily = family),
        labelMediumEmphasized = base.labelMediumEmphasized.copy(fontFamily = family),
        labelSmallEmphasized = base.labelSmallEmphasized.copy(fontFamily = family)
    )
}
