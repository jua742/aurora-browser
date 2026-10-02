package com.aurora.browser.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aurora.browser.R
import com.aurora.browser.browser.UrlResolver
import com.aurora.browser.data.db.entities.HistoryEntry
import com.aurora.browser.viewmodel.BrowserViewModel

/**
 * Home / new-tab page (spec B-2): centered logo, the 56dp search pill, then
 * "Top sites" (up to 8 tiles from most-visited history) and "Bookmarks" chips.
 * Tapping a tile/chip loads it in the current tab.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: BrowserViewModel,
    onOpenUrl: (String) -> Unit,
) {
    val topSites by viewModel.topSites.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarkChips.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(24.dp))
        // The big search/address pill: full width minus 32dp margins, 56dp tall.
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = {
                    onOpenUrl(query)
                    query = ""
                    focusManager.clearFocus(force = true)
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        )
        Spacer(Modifier.height(28.dp))

        Text(
            text = stringResource(R.string.top_sites),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(8.dp))
        if (topSites.isEmpty()) {
            Text(
                text = stringResource(R.string.top_sites_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                maxItemsInEachRow = 4,
                modifier = Modifier.fillMaxWidth(),
            ) {
                topSites.take(8).forEach { site ->
                    TopSiteTile(site = site, onClick = { onOpenUrl(site.url) })
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.home_bookmarks),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(8.dp))
        if (bookmarks.isEmpty()) {
            Text(
                text = stringResource(R.string.home_bookmarks_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(modifier = Modifier.fillMaxWidth()) {
                items(bookmarks, key = { it.bookmark.id }) { item ->
                    AssistChip(
                        onClick = { onOpenUrl(item.bookmark.url) },
                        label = {
                            Text(
                                text = item.bookmark.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
    }
}

/** One 72dp top-site tile: 40dp favicon + label. */
@Composable
private fun TopSiteTile(
    site: HistoryEntry,
    onClick: () -> Unit,
) {
    // Favicons come from Google's public favicon service, fetched with Coil
    // (favicons only, per the spec's image policy). The label is local history.
    val host = remember(site.url) { UrlResolver.hostOf(site.url) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (host != null) {
                    AsyncImage(
                        model = "https://www.google.com/s2/favicons?domain=$host&sz=64",
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                } else {
                    Text(
                        text = site.title.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = host ?: site.title.take(14),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
