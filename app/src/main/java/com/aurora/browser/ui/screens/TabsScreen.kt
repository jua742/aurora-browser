package com.aurora.browser.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.browser.R
import com.aurora.browser.browser.TabInfo
import com.aurora.browser.viewmodel.BrowserViewModel
import com.aurora.browser.viewmodel.TabsViewModel
import com.aurora.browser.viewmodel.TabsViewModelFactory

/**
 * Tab switcher (spec B-2): 2-column card grid with best-effort thumbnails,
 * title/host/close per card, Normal|Incognito segmented toggle, + New tab and
 * Close all. Incognito cards get a dark "mask" tint plus a text label
 * (never color-only signaling, spec B-19).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsScreen(
    browserViewModel: BrowserViewModel,
    onBackToBrowser: () -> Unit,
) {
    val tabsViewModel: TabsViewModel = viewModel(factory = TabsViewModelFactory(browserViewModel))
    val tabs by tabsViewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by tabsViewModel.activeTabId.collectAsStateWithLifecycle()
    var showIncognito by remember { mutableStateOf(false) }
    val visible = remember(tabs, showIncognito) {
        tabs.filter { it.isIncognito == showIncognito }
    }

    BackHandler(onBack = onBackToBrowser)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tabs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackToBrowser) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { tabsViewModel.closeAllTabs(if (showIncognito) true else false) },
                    ) {
                        Text(stringResource(R.string.close_all_tabs))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { tabsViewModel.newTab(showIncognito) }) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.cd_add_tab),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !showIncognito,
                    onClick = { showIncognito = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    label = { Text(stringResource(R.string.tab_normal)) },
                )
                SegmentedButton(
                    selected = showIncognito,
                    onClick = { showIncognito = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    label = { Text(stringResource(R.string.tab_incognito)) },
                )
            }
            Spacer(Modifier.height(12.dp))
            if (visible.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.no_open_tabs),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visible, key = { it.id }) { tab ->
                        TabCard(
                            tab = tab,
                            selected = tab.id == activeTabId,
                            onClick = {
                                tabsViewModel.setActiveTab(tab.id)
                                onBackToBrowser()
                            },
                            onClose = { tabsViewModel.closeTab(tab.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabCard(
    tab: TabInfo,
    selected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val cardDescription = stringResource(R.string.tab_card_description, tab.title ?: tab.host ?: "")
    Card(
        onClick = onClick,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier
            .aspectRatio(0.85f)
            .semantics { contentDescription = cardDescription },
    ) {
        Box(Modifier.fillMaxSize()) {
            // Thumbnail (16:10): best-effort capture, else a gradient placeholder.
            if (tab.thumbnail != null) {
                Image(
                    bitmap = tab.thumbnail.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f),
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            ),
                        ),
                ) {
                    Text(
                        text = tab.title?.firstOrNull()?.uppercase()
                            ?: tab.host?.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            // Incognito: dark mask tint + explicit text label.
            if (tab.isIncognito) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                )
                Text(
                    text = stringResource(R.string.incognito_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
            }
            // Close: 32dp visual, padded to the 48dp touch target.
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.close_tab),
                    tint = if (tab.isIncognito) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
            ) {
                Text(
                    text = tab.title?.takeIf { it.isNotBlank() } ?: tab.host ?: "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (tab.isIncognito) Color.White else Color.Unspecified,
                )
                Text(
                    text = tab.host ?: "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tab.isIncognito) {
                        Color.White.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
