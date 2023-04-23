package com.keylesspalace.tusky.usecase

import androidx.room.withTransaction
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.TimelineDao
import javax.inject.Inject

/**
 * Functionality that is only intended to be used by the "Developer Tools" menu when built
 * in debug mode.
 */
class DeveloperToolsUseCase @Inject constructor(
    private val db: AppDatabase
) {

    private var timelineDao: TimelineDao = db.timelineDao()

    /**
     * Clear the home timeline cache.
     */
    suspend fun clearHomeTimelineCache(accountId: Long) {
        timelineDao.removeAllStatuses(accountId)
    }

    /**
     * Delete first K statuses
     */
    suspend fun deleteFirstKStatuses(accountId: Long, k: Int) {
        db.withTransaction {
            val ids = timelineDao.getMostRecentNStatusIds(accountId, 40)
            timelineDao.deleteRange(accountId, ids.last(), ids.first())
        }
    }

    companion object {
        const val TAG = "DeveloperToolsUseCase"
    }
}
