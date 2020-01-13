package com.keylesspalace.tusky.components.search

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.paging.PagedList
import com.keylesspalace.tusky.components.search.adapter.SearchRepository
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.*
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.TimelineCases
import com.keylesspalace.tusky.util.*
import com.keylesspalace.tusky.viewdata.StatusViewData
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import javax.inject.Inject

class SearchViewModel @Inject constructor(
        mastodonApi: MastodonApi,
        private val timelineCases: TimelineCases,
        private val accountManager: AccountManager
) : RxAwareViewModel() {

    var currentQuery: String = ""

    var activeAccount: AccountEntity?
        get() = accountManager.activeAccount
        set(value) {
            accountManager.activeAccount = value
        }

    val mediaPreviewEnabled = activeAccount?.mediaPreviewEnabled ?: false
    val alwaysShowSensitiveMedia = activeAccount?.alwaysShowSensitiveMedia ?: false
    val alwaysOpenSpoiler = activeAccount?.alwaysOpenSpoiler ?: false

    private val statusesRepository = SearchRepository<Pair<Status, StatusViewData.Concrete>>(mastodonApi)
    private val accountsRepository = SearchRepository<Account>(mastodonApi)
    private val hashtagsRepository = SearchRepository<HashTag>(mastodonApi)

    private val repoResultStatus = MutableLiveData<Listing<Pair<Status, StatusViewData.Concrete>>>()
    val statuses: LiveData<PagedList<Pair<Status, StatusViewData.Concrete>>> = repoResultStatus.switchMap { it.pagedList }
    val networkStateStatus: LiveData<NetworkState> = repoResultStatus.switchMap { it.networkState }
    val networkStateStatusRefresh: LiveData<NetworkState> = repoResultStatus.switchMap { it.refreshState }

    private val repoResultAccount = MutableLiveData<Listing<Account>>()
    val accounts: LiveData<PagedList<Account>> = repoResultAccount.switchMap { it.pagedList }
    val networkStateAccount: LiveData<NetworkState> = repoResultAccount.switchMap { it.networkState }
    val networkStateAccountRefresh: LiveData<NetworkState> = repoResultAccount.switchMap { it.refreshState }

    private val repoResultHashTag = MutableLiveData<Listing<HashTag>>()
    val hashtags: LiveData<PagedList<HashTag>> = repoResultHashTag.switchMap { it.pagedList }
    val networkStateHashTag: LiveData<NetworkState> = repoResultHashTag.switchMap { it.networkState }
    val networkStateHashTagRefresh: LiveData<NetworkState> = repoResultHashTag.switchMap { it.refreshState }

    private val loadedStatuses = ArrayList<Pair<Status, StatusViewData.Concrete>>()
    fun search(query: String) {
        loadedStatuses.clear()
        repoResultStatus.value = statusesRepository.getSearchData(SearchType.Status, query, disposables, initialItems = loadedStatuses) {
            it?.statuses?.map { status -> Pair(status, ViewDataUtils.statusToViewData(status, alwaysShowSensitiveMedia, alwaysOpenSpoiler)!!) }
                    .orEmpty()
                    .apply {
                        loadedStatuses.addAll(this)
                    }
        }
        repoResultAccount.value = accountsRepository.getSearchData(SearchType.Account, query, disposables) {
            it?.accounts.orEmpty()
        }
        val hashtagQuery = if (query.startsWith("#")) query else "#$query"
        repoResultHashTag.value =
                hashtagsRepository.getSearchData(SearchType.Hashtag, hashtagQuery, disposables) {
                    it?.hashtags.orEmpty()
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
            val newPair = Pair(status.first, StatusViewData.Builder(status.second).setBookmarked(isBookmarked).createStatusViewData())
            loadedStatuses[idx] = newPair
            repoResultStatus.value?.refresh?.invoke()
        }
        timelineCases.bookmark(status.first, isBookmarked)
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