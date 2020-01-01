package com.keylesspalace.tusky.components.search

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.paging.PagedList
import com.keylesspalace.tusky.components.search.adapter.SearchRepository
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.*
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.TimelineCases
import com.keylesspalace.tusky.util.Listing
import com.keylesspalace.tusky.util.NetworkState
import com.keylesspalace.tusky.util.RxAwareViewModel
import com.keylesspalace.tusky.util.ViewDataUtils
import com.keylesspalace.tusky.viewdata.StatusViewData
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import javax.inject.Inject

class SearchViewModel @Inject constructor(
        mastodonApi: MastodonApi,
        private val timelineCases: TimelineCases,
        private val accountManager: AccountManager) : RxAwareViewModel() {

    var currentQuery: String = ""

    var activeAccount: AccountEntity?
        get() = accountManager.activeAccount
        set(value) {
            accountManager.activeAccount = value
        }

    val mediaPreviewEnabled: Boolean
        get() = activeAccount?.mediaPreviewEnabled ?: false

    private val statusesRepository = SearchRepository<Pair<Status, StatusViewData.Concrete>>(mastodonApi)
    private val accountsRepository = SearchRepository<Account>(mastodonApi)
    private val hashtagsRepository = SearchRepository<HashTag>(mastodonApi)
    val alwaysShowSensitiveMedia: Boolean = activeAccount?.alwaysShowSensitiveMedia
            ?: false
    val alwaysOpenSpoiler: Boolean = activeAccount?.alwaysOpenSpoiler
            ?: false

    private val repoResultStatus = MutableLiveData<Listing<Pair<Status, StatusViewData.Concrete>>>()
    val statuses: LiveData<PagedList<Pair<Status, StatusViewData.Concrete>>> = Transformations.switchMap(repoResultStatus) { it.pagedList }
    val networkStateStatus: LiveData<NetworkState> = Transformations.switchMap(repoResultStatus) { it.networkState }
    val networkStateStatusRefresh: LiveData<NetworkState> = Transformations.switchMap(repoResultStatus) { it.refreshState }

    private val repoResultAccount = MutableLiveData<Listing<Account>>()
    val accounts: LiveData<PagedList<Account>> = Transformations.switchMap(repoResultAccount) { it.pagedList }
    val networkStateAccount: LiveData<NetworkState> = Transformations.switchMap(repoResultAccount) { it.networkState }
    val networkStateAccountRefresh: LiveData<NetworkState> = Transformations.switchMap(repoResultAccount) { it.refreshState }

    private val repoResultHashTag = MutableLiveData<Listing<HashTag>>()
    val hashtags: LiveData<PagedList<HashTag>> = Transformations.switchMap(repoResultHashTag) { it.pagedList }
    val networkStateHashTag: LiveData<NetworkState> = Transformations.switchMap(repoResultHashTag) { it.networkState }
    val networkStateHashTagRefresh: LiveData<NetworkState> = Transformations.switchMap(repoResultHashTag) { it.refreshState }

    private val loadedStatuses = ArrayList<Pair<Status, StatusViewData.Concrete>>()
    fun search(query: String) {
        loadedStatuses.clear()
        repoResultStatus.value = statusesRepository.getSearchData(SearchType.Status, query, disposables, initialItems = loadedStatuses) {
            (it?.statuses?.map { status -> Pair(status, ViewDataUtils.statusToViewData(status, alwaysShowSensitiveMedia, alwaysOpenSpoiler)!!) }
                    ?: emptyList())
                    .apply {
                        loadedStatuses.addAll(this)
                    }
        }
        repoResultAccount.value = accountsRepository.getSearchData(SearchType.Account, query, disposables) {
            it?.accounts ?: emptyList()
        }
        val hashtagQuery = if (query.startsWith("#")) query else "#$query"
        repoResultHashTag.value =
                hashtagsRepository.getSearchData(SearchType.Hashtag, hashtagQuery, disposables) {
                    it?.hashtags ?: emptyList()
                }

    }

    fun removeItem(status: Pair<Status, StatusViewData.Concrete>) {
        timelineCases.delete(status.first.id)
                .subscribe({
                    if (loadedStatuses.remove(status))
                        repoResultStatus.value?.refresh?.invoke()
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
            repoResultStatus.value?.refresh?.invoke()
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
            repoResultStatus.value?.refresh?.invoke()
        }
    }

    fun contentHiddenChange(status: Pair<Status, StatusViewData.Concrete>, isShowing: Boolean) {
        val idx = loadedStatuses.indexOf(status)
        if (idx >= 0) {
            val newPair = Pair(status.first, StatusViewData.Builder(status.second).setIsShowingSensitiveContent(isShowing).createStatusViewData())
            loadedStatuses[idx] = newPair
            repoResultStatus.value?.refresh?.invoke()
        }
    }

    fun collapsedChange(status: Pair<Status, StatusViewData.Concrete>, collapsed: Boolean) {
        val idx = loadedStatuses.indexOf(status)
        if (idx >= 0) {
            val newPair = Pair(status.first, StatusViewData.Builder(status.second).setCollapsed(collapsed).createStatusViewData())
            loadedStatuses[idx] = newPair
            repoResultStatus.value?.refresh?.invoke()
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
            repoResultStatus.value?.refresh?.invoke()
        }
    }

    fun favorite(status: Pair<Status, StatusViewData.Concrete>, isFavorited: Boolean) {
        val idx = loadedStatuses.indexOf(status)
        if (idx >= 0) {
            val newPair = Pair(status.first, StatusViewData.Builder(status.second).setFavourited(isFavorited).createStatusViewData())
            loadedStatuses[idx] = newPair
            repoResultStatus.value?.refresh?.invoke()
        }
        timelineCases.favourite(status.first, isFavorited)
                .onErrorReturnItem(status.first)
                .subscribe()
                .autoDispose()
    }

    fun bookmark(status: Pair<Status, StatusViewData.Concrete>, isBookmarked: Boolean) {
        val idx = loadedStatuses.indexOf(status)
        if (idx >= 0) {
            val newPair = Pair(status.first, StatusViewData.Builder(status.second).setFavourited(isBookmarked).createStatusViewData())
            loadedStatuses[idx] = newPair
            repoResultStatus.value?.refresh?.invoke()
        }
        timelineCases.favourite(status.first, isBookmarked)
                .onErrorReturnItem(status.first)
                .subscribe()
                .autoDispose()
    }

    fun getAllAccountsOrderedByActive(): List<AccountEntity> {
        return accountManager.getAllAccountsOrderedByActive()
    }

    fun muteAcount(accountId: String) {
        timelineCases.mute(accountId)
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


    companion object {
        private const val TAG = "SearchViewModel"
    }
}