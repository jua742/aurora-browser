package com.aurora.browser.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aurora.browser.R

/**
 * Tabs button badge: open-tab count in a rounded outline box.
 * Display caps at "99+" (spec B-5); real cap is 20 normal + 10 incognito.
 */
@Composable
fun TabCounterBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.cd_tabs)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = "$description: $count" },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                .border(
                    2.dp,
                    MaterialTheme.colorScheme.onSurface,
                    RoundedCornerShape(6.dp),
                ),
        ) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}
