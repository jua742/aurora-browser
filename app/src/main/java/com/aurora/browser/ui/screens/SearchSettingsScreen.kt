package com.aurora.browser.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.browser.R
import com.aurora.browser.data.datastore.isValidSearchTemplate
import com.aurora.browser.viewmodel.SettingsViewModel

/**
 * Search engine picker: the four built-ins plus a custom URL template.
 * The template must be an https URL containing %s (validated inline); the
 * shared [isValidSearchTemplate] check is also used by the browser layer.
 */
@Composable
fun SearchSettingsScreen(onNavigateBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel()
    val engineId by viewModel.searchEngineId.collectAsState()
    val savedTemplate by viewModel.customSearchTemplate.collectAsState()

    var draft by remember(savedTemplate) { mutableStateOf(savedTemplate) }
    var showError by remember { mutableStateOf(false) }

    val engines = listOf(
        SEARCH_ENGINE_GOOGLE,
        SEARCH_ENGINE_BING,
        SEARCH_ENGINE_DUCKDUCKGO,
        SEARCH_ENGINE_YAHOO,
        SEARCH_ENGINE_CUSTOM
    )

    Scaffold(
        topBar = {
            ContentTopBar(
                title = stringResource(R.string.content_search_settings_title),
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            engines.forEach { id ->
                item(key = id) {
                    RadioRow(
                        title = stringResource(searchEngineNameRes(id)),
                        selected = engineId == id,
                        onClick = {
                            viewModel.setSearchEngineId(id)
                            showError = false
                        }
                    )
                }
            }
            if (engineId == SEARCH_ENGINE_CUSTOM) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it; showError = false },
                            label = { Text(stringResource(R.string.content_search_template_hint)) },
                            placeholder = { Text(stringResource(R.string.content_search_template_example)) },
                            singleLine = true,
                            isError = showError,
                            supportingText = {
                                Text(
                                    if (showError) stringResource(R.string.content_search_template_error)
                                    else stringResource(R.string.content_search_template_example)
                                )
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (isValidSearchTemplate(draft)) {
                                    viewModel.setCustomSearchTemplate(draft)
                                } else {
                                    showError = true
                                }
                            }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.content_search_suggestions_note),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
