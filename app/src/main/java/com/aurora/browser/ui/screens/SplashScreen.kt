package com.aurora.browser.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.browser.R
import com.aurora.browser.viewmodel.BrowserViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.snapshotFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Splash screen (spec B-2): logo (72dp) + wordmark, no buttons, no network.
 * Auto-advances ~800ms after launch, or when session restore finishes —
 * whichever is LATER, capped at 2.5s so a slow database can never trap the user.
 */
@Composable
fun SplashScreen(
    viewModel: BrowserViewModel,
    onDone: () -> Unit,
) {
    val tabsRestored by viewModel.tabsRestored.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        val start = SystemClock.uptimeMillis()
        withTimeoutOrNull(2500) {
            snapshotFlow { tabsRestored }.first { it }
        }
        val elapsed = SystemClock.uptimeMillis() - start
        if (elapsed < 800) delay(800 - elapsed)
        onDone()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}
