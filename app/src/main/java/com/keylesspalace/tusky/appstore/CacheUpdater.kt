package com.keylesspalace.tusky.appstore

import com.google.gson.Gson
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class CacheUpdater @Inject constructor(
        eventHub: EventHub,
        accountManager: AccountManager,
        private val appDatabase: AppDatabase,
        gson: Gson
) {

    private val disposable: Disposable

    init {
        val timelineDao = appDatabase.timelineDao()
        disposable = eventHub.events.subscribe { event ->
            val accountId = accountManager.activeAccount?.id ?: return@subscribe
            when (event) {
                is FavoriteEvent ->
                    if (event.statusNew != null)
                        timelineDao.setFavourited(accountId, event.statusId, event.favourite, event.statusNew.favouritesCount)
                    else
                        timelineDao.setFavourited(accountId, event.statusId, event.favourite)
                is ReblogEvent ->
                    if (event.statusNew != null)
                        timelineDao.setReblogged(accountId, event.statusId, event.reblog, event.statusNew.reblogsCount)
                    else
                        timelineDao.setReblogged(accountId, event.statusId, event.reblog)
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

    fun stop() {
        this.disposable.dispose()
    }

    fun clearForUser(accountId: Long) {
        Single.fromCallable {
            appDatabase.timelineDao().removeAllForAccount(accountId)
            appDatabase.timelineDao().removeAllUsersForAccount(accountId)
        }
                .subscribeOn(Schedulers.io())
                .subscribe()
    }
}