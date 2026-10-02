package com.aurora.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.browser.AuroraApp
import com.aurora.browser.R
import com.aurora.browser.data.db.entities.HistoryEntry
import com.aurora.browser.data.repository.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One day-group in the history list, e.g. "Today" with its entries. */
data class HistoryGroup(val title: String, val entries: List<HistoryEntry>)

/**
 * State for the History screen: searching (debounced 300 ms), day-grouped
 * listing, swipe-to-delete with undo, and clear-all.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application = application
    private val container = (application as AuroraApp).container
    private val repository: HistoryRepository = container.historyRepository

    /** Raw text in the search field; the list query below debounces it 300 ms. */
    val searchQuery = MutableStateFlow("")

    private val entries: StateFlow<List<HistoryEntry>> = searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) repository.observeHistory()
            else repository.searchHistory(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Entries grouped under "Today" / "Yesterday" / date headers, newest first. */
    val groupedHistory: StateFlow<List<HistoryGroup>> = entries
        .map { groupByDay(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The entry removed by the last swipe-delete, kept for the Undo action. */
    private var lastDeleted: HistoryEntry? = null

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun deleteEntry(entry: HistoryEntry) {
        lastDeleted = entry
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteEntry(entry.id)
        }
    }

    fun restoreDeleted() {
        val entry = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch(Dispatchers.IO) {
            repository.restoreEntry(entry)
        }
    }

    fun clearAll() {
        lastDeleted = null
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
        }
    }

    /**
     * Buckets entries by calendar day in the device timezone. Boundaries are
     * computed from "start of today" so daylight-saving transitions stay correct.
     */
    private fun groupByDay(entries: List<HistoryEntry>): List<HistoryGroup> {
        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yesterdayStart = dayStart - 24 * 60 * 60 * 1000L
        val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        val groups = linkedMapOf<String, MutableList<HistoryEntry>>()
        for (entry in entries) {
            val label = when {
                entry.lastVisited >= dayStart -> app.getString(R.string.content_history_today)
                entry.lastVisited >= yesterdayStart -> app.getString(R.string.content_history_yesterday)
                else -> dateFormat.format(Date(entry.lastVisited))
            }
            groups.getOrPut(label, ::mutableListOf).add(entry)
        }
        return groups.map { (title, list) -> HistoryGroup(title, list) }
    }
}
