package com.keylesspalace.tusky.appstore

import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.squareup.moshi.Moshi
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Updates the database cache in response to events.
 * This is important for the home timeline and notifications to be up to date.
 */
class CacheUpdater @Inject constructor(
    eventHub: EventHub,
    accountManager: AccountManager,
    appDatabase: AppDatabase,
    moshi: Moshi
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val timelineDao = appDatabase.timelineDao()
    private val statusDao = appDatabase.timelineStatusDao()
    private val notificationsDao = appDatabase.notificationsDao()

    init {
        scope.launch {
            eventHub.events.collect { event ->
                val tuskyAccountId = accountManager.activeAccount?.id ?: return@collect
                when (event) {
                    is StatusChangedEvent -> statusDao.update(tuskyAccountId = tuskyAccountId, status = event.status)
                    is UnfollowEvent -> timelineDao.removeStatusesAndReblogsByUser(tuskyAccountId, event.accountId)
                    is BlockEvent -> removeAllByUser(tuskyAccountId, event.accountId)
                    is MuteEvent -> removeAllByUser(tuskyAccountId, event.accountId)

                    is DomainMuteEvent -> {
                        timelineDao.deleteAllFromInstance(tuskyAccountId, event.instance)
                        notificationsDao.deleteAllFromInstance(tuskyAccountId, event.instance)
                    }

                    is StatusDeletedEvent -> {
                        timelineDao.deleteAllWithStatus(tuskyAccountId, event.statusId)
                        notificationsDao.deleteAllWithStatus(tuskyAccountId, event.statusId)
                    }

                    is PollVoteEvent -> statusDao.setVoted(tuskyAccountId, event.statusId, event.poll)
                    is PollShowResultsEvent -> statusDao.setShowResults(tuskyAccountId, event.statusId)
                }
            }
        }
    }

    private suspend fun removeAllByUser(tuskyAccountId: Long, accountId: String) {
        timelineDao.removeAllByUser(tuskyAccountId, accountId)
        notificationsDao.removeAllByUser(tuskyAccountId, accountId)
    }

    fun stop() {
        this.scope.cancel()
    }
}
