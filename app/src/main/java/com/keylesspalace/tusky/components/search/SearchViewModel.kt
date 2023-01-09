/* Copyright 2021 Tusky Contributors
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.components.search

import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import at.connyduck.calladapter.networkresult.NetworkResult
import com.keylesspalace.tusky.components.search.adapter.SearchPagingSourceFactory
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.DeletedStatus
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.usecase.TimelineCases
import com.keylesspalace.tusky.util.RxAwareViewModel
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
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

    private val loadedStatuses: MutableList<StatusViewData.Concrete> = mutableListOf()

    private val statusesPagingSourceFactory = SearchPagingSourceFactory(mastodonApi, SearchType.Status, loadedStatuses) {
        it.statuses.map { status ->
            status.toViewData(
                isShowingContent = alwaysShowSensitiveMedia || !status.actionableStatus.sensitive,
                isExpanded = alwaysOpenSpoiler,
                isCollapsed = true
            )
        }.apply {
            loadedStatuses.addAll(this)
        }
    }
    private val accountsPagingSourceFactory = SearchPagingSourceFactory(mastodonApi, SearchType.Account) {
        it.accounts
    }
    private val hashtagsPagingSourceFactory = SearchPagingSourceFactory(mastodonApi, SearchType.Hashtag) {
        it.hashtags
    }

    val statusesFlow = Pager(
        config = PagingConfig(pageSize = DEFAULT_LOAD_SIZE, initialLoadSize = DEFAULT_LOAD_SIZE),
        pagingSourceFactory = statusesPagingSourceFactory
    ).flow
        .cachedIn(viewModelScope)

    val accountsFlow = Pager(
        config = PagingConfig(pageSize = DEFAULT_LOAD_SIZE, initialLoadSize = DEFAULT_LOAD_SIZE),
        pagingSourceFactory = accountsPagingSourceFactory
    ).flow
        .cachedIn(viewModelScope)

    val hashtagsFlow = Pager(
        config = PagingConfig(pageSize = DEFAULT_LOAD_SIZE, initialLoadSize = DEFAULT_LOAD_SIZE),
        pagingSourceFactory = hashtagsPagingSourceFactory
    ).flow
        .cachedIn(viewModelScope)

    fun search(query: String) {
        loadedStatuses.clear()
        statusesPagingSourceFactory.newSearch(query)
        accountsPagingSourceFactory.newSearch(query)
        hashtagsPagingSourceFactory.newSearch(query)
    }

    fun removeItem(statusViewData: StatusViewData.Concrete) {
        viewModelScope.launch {
            if (timelineCases.delete(statusViewData.id).isSuccess) {
                if (loadedStatuses.remove(statusViewData)) {
                    statusesPagingSourceFactory.invalidate()
                }
            }
        }
    }

    fun expandedChange(statusViewData: StatusViewData.Concrete, expanded: Boolean) {
        updateStatusViewData(statusViewData.copy(isExpanded = expanded))
    }

    fun reblog(statusViewData: StatusViewData.Concrete, reblog: Boolean) {
        timelineCases.reblog(statusViewData.id, reblog)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { setRebloggedForStatus(statusViewData, reblog) },
                { t -> Log.d(TAG, "Failed to reblog status ${statusViewData.id}", t) }
            )
            .autoDispose()
    }

    private fun setRebloggedForStatus(statusViewData: StatusViewData.Concrete, reblog: Boolean) {
        updateStatus(
            statusViewData.status.copy(
                reblogged = reblog,
                reblog = statusViewData.status.reblog?.copy(reblogged = reblog)
            )
        )
    }

    fun contentHiddenChange(statusViewData: StatusViewData.Concrete, isShowing: Boolean) {
        updateStatusViewData(statusViewData.copy(isShowingContent = isShowing))
    }

    fun collapsedChange(statusViewData: StatusViewData.Concrete, collapsed: Boolean) {
        updateStatusViewData(statusViewData.copy(isCollapsed = collapsed))
    }

    fun voteInPoll(statusViewData: StatusViewData.Concrete, choices: MutableList<Int>) {
        val votedPoll = statusViewData.status.actionableStatus.poll!!.votedCopy(choices)
        updateStatus(statusViewData.status.copy(poll = votedPoll))
        timelineCases.voteInPoll(statusViewData.id, votedPoll.id, choices)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnError { t -> Log.d(TAG, "Failed to vote in poll: ${statusViewData.id}", t) }
            .subscribe()
            .autoDispose()
    }

    fun favorite(statusViewData: StatusViewData.Concrete, isFavorited: Boolean) {
        updateStatus(statusViewData.status.copy(favourited = isFavorited))
        timelineCases.favourite(statusViewData.id, isFavorited)
            .onErrorReturnItem(statusViewData.status)
            .subscribe()
            .autoDispose()
    }

    fun bookmark(statusViewData: StatusViewData.Concrete, isBookmarked: Boolean) {
        updateStatus(statusViewData.status.copy(bookmarked = isBookmarked))
        timelineCases.bookmark(statusViewData.id, isBookmarked)
            .onErrorReturnItem(statusViewData.status)
            .subscribe()
            .autoDispose()
    }

    fun muteAccount(accountId: String, notifications: Boolean, duration: Int?) {
        viewModelScope.launch {
            timelineCases.mute(accountId, notifications, duration)
        }
    }

    fun pinAccount(status: Status, isPin: Boolean) {
        timelineCases.pin(status.id, isPin)
    }

    fun blockAccount(accountId: String) {
        viewModelScope.launch {
            timelineCases.block(accountId)
        }
    }

    fun deleteStatusAsync(id: String): Deferred<NetworkResult<DeletedStatus>> {
        return viewModelScope.async {
            timelineCases.delete(id)
        }
    }

    fun muteConversation(statusViewData: StatusViewData.Concrete, mute: Boolean) {
        updateStatus(statusViewData.status.copy(muted = mute))
        timelineCases.muteConversation(statusViewData.id, mute)
            .onErrorReturnItem(statusViewData.status)
            .subscribe()
            .autoDispose()
    }

    private fun updateStatusViewData(newStatusViewData: StatusViewData.Concrete) {
        val idx = loadedStatuses.indexOfFirst { it.id == newStatusViewData.id }
        if (idx >= 0) {
            loadedStatuses[idx] = newStatusViewData
            statusesPagingSourceFactory.invalidate()
        }
    }

    private fun updateStatus(newStatus: Status) {
        val statusViewData = loadedStatuses.find { it.id == newStatus.id }
        if (statusViewData != null) {
            updateStatusViewData(statusViewData.copy(status = newStatus))
        }
    }

    companion object {
        private const val TAG = "SearchViewModel"
        private const val DEFAULT_LOAD_SIZE = 20
    }
}
