package com.keylesspalace.tusky.usecase

import android.util.Log
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
     * Create a gap in the home timeline to make it easier to interactively experiment with
     * different "Load more" behaviours.
     *
     * Do this by taking the 10 most recent statuses, keeping the first 2, deleting the next 7,
     * and replacing the last one with a placeholder.
     */
    suspend fun createLoadMoreGap(accountId: Long) {
        db.withTransaction {
            val ids = timelineDao.getMostRecentNStatusIds(accountId, 10)
            val maxId = ids[2]
            val minId = ids[8]
            val placeHolderId = ids[9]

            Log.d(
                "TAG",
                "createLoadMoreGap: creating gap between $minId .. $maxId (new placeholder: $placeHolderId"
            )

            timelineDao.deleteRange(accountId, minId, maxId)
            timelineDao.convertStatustoPlaceholder(placeHolderId)
        }
    }

    companion object {
        const val TAG = "DeveloperToolsUseCase"
    }
}
