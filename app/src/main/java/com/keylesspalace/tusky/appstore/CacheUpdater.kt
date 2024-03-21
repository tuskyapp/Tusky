package com.keylesspalace.tusky.appstore

import com.google.gson.Gson
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CacheUpdater @Inject constructor(
    eventHub: EventHub,
    accountManager: AccountManager,
    appDatabase: AppDatabase,
    gson: Gson
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        val timelineDao = appDatabase.timelineDao()

        scope.launch {
            eventHub.events.collect { event ->
                val accountId = accountManager.activeAccount?.id ?: return@collect
                when (event) {
                    is StatusChangedEvent -> {
                        val status = event.status
                        timelineDao.update(
                            tuskyAccountId = accountId,
                            status = status,
                            gson = gson
                        )
                    }
                    is UnfollowEvent ->
                        timelineDao.removeAllByUser(accountId, event.accountId)
                    is StatusDeletedEvent ->
                        timelineDao.delete(accountId, event.statusId)
                    is PollVoteEvent -> {
                        val pollString = gson.toJson(event.poll)
                        timelineDao.setVoted(accountId, event.statusId, pollString)
                    }
                }
            }
        }
    }

    fun stop() {
        this.scope.cancel()
    }
}
