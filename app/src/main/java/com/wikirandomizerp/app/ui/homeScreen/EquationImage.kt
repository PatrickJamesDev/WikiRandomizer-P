package com.wikirandomizerp.app.ui.homeScreen

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.wikirandomizerp.app.R
import com.wikirandomizerp.app.ui.theme.ColorConstants.colorMatrixInvert

@Composable
fun EquationImage(
    context: Context,
    dpi: Float,
    latex: String,
    fontSize: Int,
    darkTheme: Boolean
) {
    // The formula goes into a URL query: unescaped '+', '&', '#', '%' or spaces changed its meaning
    // (e.g. "x+1" rendered as "x 1") or made the URL invalid. Encode it.
    val formula = remember(latex, dpi, fontSize) {
        Uri.encode("\\dpi{${(dpi * 160 * (fontSize / 16.0)).toInt()}}${latex.trim()}")
    }
    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data("https://latex.codecogs.com/png.image?$formula")
                .size(Size.ORIGINAL)
                .build(),
            placeholder = painterResource(R.drawable.more_horiz),
            error = painterResource(R.drawable.error),
            contentDescription = null,
            colorFilter = if (darkTheme) // Invert colors in dark theme
                ColorFilter.colorMatrix(ColorMatrix(colorMatrixInvert))
            else null,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}