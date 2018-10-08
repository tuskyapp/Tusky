package com.keylesspalace.tusky.appstore

import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import io.reactivex.disposables.Disposable
import javax.inject.Inject

class CacheUpdater @Inject constructor(
        eventHub: EventHub,
        appDatabase: AppDatabase,
        accountManager: AccountManager
) {

    private val disposable: Disposable

    init {
        val timelineDao = appDatabase.timelineDao()
        disposable = eventHub.events.subscribe { event ->
            val accountId = accountManager.activeAccount?.id ?: return@subscribe
            when (event) {
                is FavoriteEvent ->
                    timelineDao.setFavourited(accountId, event.statusId, event.favourite)
                is ReblogEvent ->
                    timelineDao.setReblogged(accountId, event.statusId, event.reblog)
                is UnfollowEvent ->
                    timelineDao.removeAllByUser(accountId, event.accountId)
                is StatusDeletedEvent ->
                    timelineDao.delete(accountId, event.statusId)
            }
        }
    }

    fun stop() {
        this.disposable.dispose()
    }
}