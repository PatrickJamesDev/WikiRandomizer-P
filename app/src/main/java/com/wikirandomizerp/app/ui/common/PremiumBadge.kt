package com.wikirandomizerp.app.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wikirandomizerp.app.R

/** Small, recognisable "Premium" pill shown next to every paid feature. */
@Composable
fun PremiumBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xFFB8860B),
        contentColor = Color.White,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = stringResource(R.string.premium),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(PaddingValues(horizontal = 6.dp, vertical = 2.dp))
        )
    }
}
