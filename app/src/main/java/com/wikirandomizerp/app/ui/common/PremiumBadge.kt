package com.wikirandomizerp.app.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(PaddingValues(horizontal = 6.dp, vertical = 2.dp))
        ) {
            Icon(
                painter = painterResource(R.drawable.premium),
                contentDescription = stringResource(R.string.premiumBadgeDesc),
                modifier = Modifier.size(11.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = stringResource(R.string.premium),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
