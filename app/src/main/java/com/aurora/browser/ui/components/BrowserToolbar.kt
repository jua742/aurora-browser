package com.aurora.browser.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aurora.browser.R

/**
 * The 56dp browser toolbar (spec B-2):
 * [Back][Forward][address pill][Reload/Stop morph][tabs badge][menu],
 * with a 1dp divider underneath. The 3dp progress bar lives in BrowserScreen.
 */
@Composable
fun BrowserToolbar(
    displayText: String,
    isSecure: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLoading: Boolean,
    bookmarked: Boolean,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onAddressClick: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onToggleBookmark: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stopDescription = stringResource(R.string.cd_stop)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = canGoBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
            )
        }
        IconButton(onClick = onForward, enabled = canGoForward) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.cd_forward),
            )
        }
        AddressBarDisplay(
            displayText = displayText,
            isSecure = isSecure,
            bookmarked = bookmarked,
            onClick = onAddressClick,
            onToggleBookmark = onToggleBookmark,
            modifier = Modifier.weight(1f),
        )
        // Reload/Stop morph: circular progress while loading — tapping it stops.
        if (isLoading) {
            IconButton(
                onClick = onStop,
                modifier = Modifier.semantics { contentDescription = stopDescription },
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(12.dp),
                )
            }
        } else {
            IconButton(onClick = onReload) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.cd_reload),
                )
            }
        }
        IconButton(onClick = onTabsClick) {
            TabCounterBadge(count = tabCount)
        }
        IconButton(onClick = onMenuClick) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.cd_menu),
            )
        }
    }
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
