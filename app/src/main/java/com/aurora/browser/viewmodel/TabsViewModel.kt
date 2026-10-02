package com.aurora.browser.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aurora.browser.browser.TabInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin ViewModel for the tab switcher screen. All real tab state lives in
 * [BrowserViewModel] (which owns the TabManager); this just delegates so the
 * Tabs screen has its own lifecycle-friendly holder.
 */
class TabsViewModel(
    private val browserViewModel: BrowserViewModel,
) : ViewModel() {

    val tabs: StateFlow<List<TabInfo>> = browserViewModel.tabs
    val activeTabId: StateFlow<String?> = browserViewModel.activeTabId

    fun newTab(incognito: Boolean) = browserViewModel.newTab(incognito)
    fun closeTab(id: String) = browserViewModel.closeTab(id)
    fun setActiveTab(id: String) = browserViewModel.setActiveTab(id)

    /** [incognito] null = close everything; true/false = close that segment only. */
    fun closeAllTabs(incognito: Boolean?) = browserViewModel.closeAllTabs(incognito)
}

class TabsViewModelFactory(
    private val browserViewModel: BrowserViewModel,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TabsViewModel::class.java)) {
            return TabsViewModel(browserViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
