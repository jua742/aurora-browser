package com.aurora.browser.data.repository

import com.aurora.browser.data.db.daos.TabDao
import com.aurora.browser.data.db.entities.TabRecord

/**
 * Session persistence: the set of open non-incognito tabs, saved on a regular
 * basis so the session can be rebuilt (by re-loading each URL) after the app
 * process is killed. Incognito tabs are never passed in.
 */
class TabRepository(private val tabDao: TabDao) {

    /** Replace-all save in one transaction: stale tabs never linger. */
    suspend fun saveTabs(tabs: List<TabRecord>) = tabDao.replaceAll(tabs)

    /** Ordered by position, for the tab switcher / session restore. */
    suspend fun loadTabs(): List<TabRecord> = tabDao.getAllOrdered()

    suspend fun clearTabs() = tabDao.clearAll()
}
