package com.aurora.browser.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.browser.R
import com.aurora.browser.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

/**
 * Appearance: theme (system / light / dark), WebView text size (50–200%),
 * and algorithmic darkening of web content in dark theme.
 * Everything applies live — the browser layer collects these flows.
 */
@Composable
fun AppearanceSettingsScreen(onNavigateBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel()
    val themeMode by viewModel.themeMode.collectAsState()
    val textZoomPercent by viewModel.textZoomPercent.collectAsState()
    val webDarkening by viewModel.webDarkening.collectAsState()

    Scaffold(
        topBar = {
            ContentTopBar(
                title = stringResource(R.string.content_appearance_title),
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            item { SectionHeader(R.string.content_appearance_theme) }
            listOf("system", "light", "dark").forEach { mode ->
                item(key = mode) {
                    RadioRow(
                        title = stringResource(themeModeNameRes(mode)),
                        selected = themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) }
                    )
                }
            }

            item { SectionHeader(R.string.content_appearance_text_size) }
            item {
                // 10%-step slider over 50..200 → 15 intervals → steps = 14.
                var sliderPosition by remember(textZoomPercent) {
                    mutableFloatStateOf(textZoomPercent.toFloat())
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "${sliderPosition.roundToInt()}%",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Slider(
                        value = sliderPosition,
                        onValueChange = { sliderPosition = it },
                        onValueChangeFinished = {
                            viewModel.setTextZoomPercent(sliderPosition.roundToInt())
                        },
                        valueRange = 50f..200f,
                        steps = 14,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                SwitchRow(
                    title = stringResource(R.string.content_appearance_web_darkening),
                    supporting = stringResource(R.string.content_appearance_web_darkening_note),
                    checked = webDarkening,
                    onCheckedChange = viewModel::setWebDarkening
                )
            }
        }
    }
}
