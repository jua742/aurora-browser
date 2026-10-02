package com.aurora.browser.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.unit.dp
import com.aurora.browser.R
import com.aurora.browser.viewmodel.BrowserViewModel

/**
 * Find-in-page overlay bar (spec B-2): docked above the keyboard with
 * [query field][up][down][close] and a "3/12" match counter.
 */
@Composable
fun FindInPageBar(
    state: BrowserViewModel.FindState,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(state.query) }
    val focusRequester = remember { FocusRequester() }

    Surface(
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            TextField(
                value = text,
                onValueChange = {
                    text = it
                    onQueryChange(it)
                },
                placeholder = { Text(stringResource(R.string.find_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
            if (state.totalMatches > 0) {
                Text(
                    text = stringResource(
                        R.string.find_counter,
                        state.activeMatch + 1,
                        state.totalMatches,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            IconButton(onClick = onPrevious) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.cd_find_up),
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.cd_find_down),
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_find_close),
                )
            }
            Spacer(Modifier.width(4.dp))
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
