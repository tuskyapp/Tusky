package tech.bigfig.roma.appstore

import tech.bigfig.roma.db.AccountManager
import tech.bigfig.roma.db.AppDatabase
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class CacheUpdater @Inject constructor(
        eventHub: EventHub,
        accountManager: AccountManager,
        private val appDatabase: AppDatabase
) {

    private val disposable: Disposable

    init {
        val timelineDao = appDatabase.timelineDao()
        disposable = eventHub.events.subscribe { event ->
            val accountId = accountManager.activeAccount?.id ?: return@subscribe
            when (event) {
                is FavouriteEvent ->
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

    fun clearForUser(accountId: Long) {
        Single.fromCallable {
            appDatabase.timelineDao().removeAllForAccount(accountId)
            appDatabase.timelineDao().removeAllUsersForAccount(accountId)
        }
                .subscribeOn(Schedulers.io())
                .subscribe()
    }
}