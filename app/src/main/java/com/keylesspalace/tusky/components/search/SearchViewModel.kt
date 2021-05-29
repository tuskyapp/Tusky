package com.keylesspalace.tusky.components.search

import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.keylesspalace.tusky.components.search.adapter.SearchPagingSourceFactory
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.DeletedStatus
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.TimelineCases
import com.keylesspalace.tusky.util.RxAwareViewModel
import com.keylesspalace.tusky.util.ViewDataUtils
import com.keylesspalace.tusky.viewdata.StatusViewData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import java.util.concurrent.Executors
import javax.inject.Inject

class SearchViewModel @Inject constructor(
        mastodonApi: MastodonApi,
        private val timelineCases: TimelineCases,
        private val accountManager: AccountManager
) : RxAwareViewModel() {

    private val executor = Executors.newSingleThreadExecutor()

    var currentQuery: String = ""

    var activeAccount: AccountEntity?
        get() = accountManager.activeAccount
        set(value) {
            accountManager.activeAccount = value
        }

    val mediaPreviewEnabled = activeAccount?.mediaPreviewEnabled ?: false
    val alwaysShowSensitiveMedia = activeAccount?.alwaysShowSensitiveMedia ?: false
    val alwaysOpenSpoiler = activeAccount?.alwaysOpenSpoiler ?: false

    private val loadedStatuses: MutableList<Pair<Status, StatusViewData.Concrete>> = mutableListOf()

    private val statusesPagingSourceFactory = SearchPagingSourceFactory(mastodonApi, SearchType.Status, "", executor, loadedStatuses){
        it?.statuses?.map { status -> Pair(status, ViewDataUtils.statusToViewData(status, alwaysShowSensitiveMedia, alwaysOpenSpoiler)!!) }
            .orEmpty()
            .apply {
                loadedStatuses.addAll(this)
            }
    }
    private val accountsPagingSourceFactory = SearchPagingSourceFactory(mastodonApi, SearchType.Account, "", executor){
        it?.accounts.orEmpty()
    }
    private val hashtagsPagingSourceFactory = SearchPagingSourceFactory(mastodonApi, SearchType.Hashtag, "", executor){
        it?.hashtags.orEmpty()
    }

    val statusesFlow = Pager(
        config = PagingConfig(pageSize = 20),
        pagingSourceFactory = statusesPagingSourceFactory
    ).flow
        .cachedIn(viewModelScope)

    val accountsFlow = Pager(
        config = PagingConfig(pageSize = 20),
        pagingSourceFactory = accountsPagingSourceFactory
    ).flow
        .cachedIn(viewModelScope)

    val hashtagsFlow = Pager(
        config = PagingConfig(pageSize = 20),
        pagingSourceFactory = hashtagsPagingSourceFactory
    ).flow
        .cachedIn(viewModelScope)

    fun search(query: String) {
        loadedStatuses.clear()
        statusesPagingSourceFactory.newSearch(query)
        accountsPagingSourceFactory.newSearch(query)
        hashtagsPagingSourceFactory.newSearch(query)
    }

    fun removeItem(status: Pair<Status, StatusViewData.Concrete>) {
        timelineCases.delete(status.first.id)
                .subscribe({
                    if (loadedStatuses.remove(status))
                        statusesPagingSourceFactory.invalidate()
                }, {
                    err -> Log.d(TAG, "Failed to delete status", err)
                })
                .autoDispose()

    }

    fun expandedChange(status: Pair<Status, StatusViewData.Concrete>, expanded: Boolean) {
        val idx = loadedStatuses.indexOf(status)
        if (idx >= 0) {
            val newPair = Pair(status.first, StatusViewData.Builder(status.second).setIsExpanded(expanded).createStatusViewData())
            loadedStatuses[idx] = newPair
            statusesPagingSourceFactory.invalidate()
        }
    }

    fun reblog(status: Pair<Status, StatusViewData.Concrete>, reblog: Boolean) {
        timelineCases.reblog(status.first, reblog)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { setRebloggedForStatus(status, reblog) },
                        { err -> Log.d(TAG, "Failed to reblog status ${status.first.id}", err) }
                )
                .autoDispose()
    }

    private fun setRebloggedForStatus(status: Pair<Status, StatusViewData.Concrete>, reblog: Boolean) {
        status.first.reblogged = reblog
        status.first.reblog?.reblogged = reblog

        val idx = loadedStatuses.indexOf(status)
        if (idx >= 0) {
            val newPair = Pair(status.first, StatusViewData.Builder(status.second).setReblogged(reblog).createStatusViewData())
            loadedStatuses[idx] = newPair
            statusesPagingSourceFactory.invalidate()
        }
    }

    fun contentHiddenChange(status: Pair<Status, StatusViewData.Concrete>, isShowing: Boolean) {
        val idx = loadedStatuses.indexOf(status)
        if (idx >= 0) {
            val newPair = Pair(status.first, StatusViewData.Builder(status.second).setIsShowingSensitiveContent(isShowing).createStatusViewData())
            loadedStatuses[idx] = newPair
            statusesPagingSourceFactory.invalidate()
        }
    }

    fun collapsedChange(status: Pair<Status, StatusViewData.Concrete>, collapsed: Boolean) {
        val idx = loadedStatuses.indexOf(status)
        if (idx >= 0) {
            val newPair = Pair(status.first, StatusViewData.Builder(status.second).setCollapsed(collapsed).createStatusViewData())
            loadedStatuses[idx] = newPair
            statusesPagingSourceFactory.invalidate()
        }
    }

    fun voteInPoll(status: Pair<Status, StatusViewData.Concrete>, choices: MutableList<Int>) {
        val votedPoll = status.first.actionableStatus.poll!!.votedCopy(choices)
        updateStatus(status, votedPoll)
        timelineCases.voteInPoll(status.first, choices)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { newPoll -> updateStatus(status, newPoll) },
                        { t ->
                            Log.d(TAG,
                                    "Failed to vote in poll: ${status.first.id}", t)
                        }
                )
                .autoDispose()
    }

    private fun updateStatus(status: Pair<Status, StatusViewData.Concrete>, newPoll: Poll) {
        val idx = loadedStatuses.indexOf(status)
        if (idx >= 0) {

            val newViewData = StatusViewData.Builder(status.second)
                    .setPoll(newPoll)
                    .createStatusViewData()
            loadedStatuses[idx] = Pair(status.first, newViewData)
            statusesPagingSourceFactory.invalidate()
        }
    }

    fun favorite(status: Pair<Status, StatusViewData.Concrete>, isFavorited: Boolean) {
        val idx = loadedStatuses.indexOf(status)
        if (idx >= 0) {
            val newPair = Pair(status.first, StatusViewData.Builder(status.second).setFavourited(isFavorited).createStatusViewData())
            loadedStatuses[idx] = newPair
            statusesPagingSourceFactory.invalidate()
        }
        timelineCases.favourite(status.first, isFavorited)
                .onErrorReturnItem(status.first)
                .subscribe()
                .autoDispose()
    }

    fun bookmark(status: Pair<Status, StatusViewData.Concrete>, isBookmarked: Boolean) {
        val idx = loadedStatuses.indexOf(status)
        if (idx >= 0) {
            val newPair = Pair(status.first, StatusViewData.Builder(status.second).setBookmarked(isBookmarked).createStatusViewData())
            loadedStatuses[idx] = newPair
            statusesPagingSourceFactory.invalidate()
        }
        timelineCases.bookmark(status.first, isBookmarked)
                .onErrorReturnItem(status.first)
                .subscribe()
                .autoDispose()
    }

    fun getAllAccountsOrderedByActive(): List<AccountEntity> {
        return accountManager.getAllAccountsOrderedByActive()
    }

    fun muteAccount(accountId: String, notifications: Boolean, duration: Int?) {
        timelineCases.mute(accountId, notifications, duration)
    }

    fun pinAccount(status: Status, isPin: Boolean) {
        timelineCases.pin(status, isPin)
    }

    fun blockAccount(accountId: String) {
        timelineCases.block(accountId)
    }

    fun deleteStatus(id: String): Single<DeletedStatus> {
        return timelineCases.delete(id)
    }

    fun retryAllSearches() {
        search(currentQuery)
    }

    fun muteConversation(status: Pair<Status, StatusViewData.Concrete>, mute: Boolean) {
        val idx = loadedStatuses.indexOf(status)
        if (idx >= 0) {
            val newPair = Pair(status.first, StatusViewData.Builder(status.second).setMuted(mute).createStatusViewData())
            loadedStatuses[idx] = newPair
            statusesPagingSourceFactory.invalidate()
        }
        timelineCases.muteConversation(status.first, mute)
                .onErrorReturnItem(status.first)
                .subscribe()
                .autoDispose()
    }

    companion object {
        private const val TAG = "SearchViewModel"
    }
}