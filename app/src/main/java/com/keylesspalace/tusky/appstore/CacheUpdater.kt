package com.keylesspalace.tusky.appstore

import com.google.gson.Gson
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject

class CacheUpdater @Inject constructor(
    eventHub: EventHub,
    private val accountManager: AccountManager,
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
                    timelineDao.setFavourited(accountId, event.statusId, event.favourite)
                is ReblogEvent ->
                    timelineDao.setReblogged(accountId, event.statusId, event.reblog)
                is BookmarkEvent ->
                    timelineDao.setBookmarked(accountId, event.statusId, event.bookmark)
                is UnfollowEvent ->
                    timelineDao.removeAllByUser(accountId, event.accountId)
                is StatusDeletedEvent ->
                    timelineDao.delete(accountId, event.statusId)
                is PollVoteEvent -> {
                    val pollString = gson.toJson(event.poll)
                    timelineDao.setVoted(accountId, event.statusId, pollString)
                }
                is PinEvent ->
                    timelineDao.setPinned(accountId, event.statusId, event.pinned)
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

    suspend fun cleanupDbForCurrentAccount() {
        accountManager.activeAccount?.id?.let { accountId ->
            appDatabase.timelineDao().cleanup(accountId, MAX_STATUSES_IN_CACHE)
            appDatabase.timelineDao().cleanupAccounts(accountId)
        }
    }

    companion object {
        private const val MAX_STATUSES_IN_CACHE = 1000
    }
}
