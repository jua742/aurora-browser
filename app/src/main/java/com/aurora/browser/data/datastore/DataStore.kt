package com.aurora.browser.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * The app's single DataStore for user settings.
 *
 * This is a top-level extension property (the canonical DataStore pattern): one
 * singleton per process, created lazily on first use. The file name is part of
 * the on-device storage contract — never rename it, or users lose settings.
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aurora_settings")
