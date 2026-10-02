package com.aurora.browser.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.browser.R
import com.aurora.browser.viewmodel.BrowserViewModel

/**
 * The address pill shown in the toolbar (spec B-2): 28dp pill with the HTTPS
 * lock on the left, the page title/host, and the bookmark star on the right.
 * Tapping it switches the toolbar into [AddressBarEditor].
 */
@Composable
fun AddressBarDisplay(
    displayText: String,
    isSecure: Boolean,
    bookmarked: Boolean,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.height(44.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
        ) {
            Icon(
                imageVector = if (isSecure) Icons.Filled.Lock else Icons.Filled.Search,
                contentDescription = stringResource(
                    if (isSecure) R.string.cd_secure else R.string.cd_not_secure,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = displayText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            // Toolbar star: filled = bookmarked (spec B-6).
            IconButton(onClick = onToggleBookmark, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = stringResource(
                        if (bookmarked) R.string.cd_bookmark_remove else R.string.cd_bookmark_add,
                    ),
                    tint = if (bookmarked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * Full-width edit mode for the address bar: text field plus the local-only
 * suggestions dropdown (history + bookmarks, max 6 — spec B-4: no network
 * suggestions in v1, so no keystroke ever leaves the device).
 */
@Composable
fun AddressBarEditor(
    initialText: String,
    suggestions: List<BrowserViewModel.Suggestion>,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }

    BackHandler(onBack = onDismiss)

    Column(modifier = modifier) {
        TextField(
            value = text,
            onValueChange = {
                text = it
                onQueryChange(it)
            },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null)
            },
            trailingIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_find_close),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit(text) }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .focusRequester(focusRequester),
        )
        if (suggestions.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(suggestions, key = { it.url }) { suggestion ->
                    SuggestionRow(
                        suggestion = suggestion,
                        onClick = { onSubmit(suggestion.url) },
                    )
                }
            }
        }
    }

    // Open the keyboard as soon as edit mode appears.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun SuggestionRow(
    suggestion: BrowserViewModel.Suggestion,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = if (suggestion.isBookmark) Icons.Filled.Star else Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = suggestion.title.ifBlank { suggestion.url },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = suggestion.url,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
