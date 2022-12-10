/* Copyright 2022 Tusky Contributors
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

package com.keylesspalace.tusky.components.viewthread

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.getOrElse
import com.keylesspalace.tusky.appstore.BlockEvent
import com.keylesspalace.tusky.appstore.BookmarkEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.FavoriteEvent
import com.keylesspalace.tusky.appstore.PinEvent
import com.keylesspalace.tusky.appstore.ReblogEvent
import com.keylesspalace.tusky.appstore.StatusComposedEvent
import com.keylesspalace.tusky.appstore.StatusDeletedEvent
import com.keylesspalace.tusky.components.timeline.util.ifExpected
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.usecase.TimelineCases
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.rx3.await
import javax.inject.Inject

class ViewThreadViewModel @Inject constructor(
    private val api: MastodonApi,
    private val filterModel: FilterModel,
    private val timelineCases: TimelineCases,
    eventHub: EventHub,
    accountManager: AccountManager
) : ViewModel() {

    private val _uiState: MutableStateFlow<ThreadUiState> = MutableStateFlow(ThreadUiState.Loading)
    val uiState: Flow<ThreadUiState>
        get() = _uiState

    private val _errors = MutableSharedFlow<Throwable>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val errors: Flow<Throwable>
        get() = _errors

    var isInitialLoad: Boolean = true

    private val alwaysShowSensitiveMedia: Boolean
    private val alwaysOpenSpoiler: Boolean

    init {
        val activeAccount = accountManager.activeAccount
        alwaysShowSensitiveMedia = activeAccount?.alwaysShowSensitiveMedia ?: false
        alwaysOpenSpoiler = activeAccount?.alwaysOpenSpoiler ?: false

        viewModelScope.launch {
            eventHub.events
                .asFlow()
                .collect { event ->
                    when (event) {
                        is FavoriteEvent -> handleFavEvent(event)
                        is ReblogEvent -> handleReblogEvent(event)
                        is BookmarkEvent -> handleBookmarkEvent(event)
                        is PinEvent -> handlePinEvent(event)
                        is BlockEvent -> removeAllByAccountId(event.accountId)
                        is StatusComposedEvent -> handleStatusComposedEvent(event)
                        is StatusDeletedEvent -> handleStatusDeletedEvent(event)
                    }
                }
        }

        loadFilters()
    }

    fun loadThread(id: String) {
        viewModelScope.launch {
            val contextCall = async { api.statusContext(id) }
            val statusCall = async { api.statusAsync(id) }

            val contextResult = contextCall.await()
            val statusResult = statusCall.await()

            val status = statusResult.getOrElse { exception ->
                _uiState.value = ThreadUiState.Error(exception)
                return@launch
            }

            contextResult.fold({ statusContext ->

                val ancestors = statusContext.ancestors.map { status -> status.toViewData() }.filter()
                val detailedStatus = status.toViewData(true)
                val descendants = statusContext.descendants.map { status -> status.toViewData() }.filter()
                val statuses = ancestors + detailedStatus + descendants

                _uiState.value = ThreadUiState.Success(
                    statuses = statuses,
                    revealButton = statuses.getRevealButtonState(),
                    refreshing = false
                )
            }, { throwable ->
                _errors.emit(throwable)
                _uiState.value = ThreadUiState.Success(
                    statuses = listOf(status.toViewData(true)),
                    revealButton = RevealButtonState.NO_BUTTON,
                    refreshing = false
                )
            })
        }
    }

    fun retry(id: String) {
        _uiState.value = ThreadUiState.Loading
        loadThread(id)
    }

    fun refresh(id: String) {
        updateSuccess { uiState ->
            uiState.copy(refreshing = true)
        }
        loadThread(id)
    }

    fun detailedStatus(): StatusViewData.Concrete? {
        return (_uiState.value as ThreadUiState.Success?)?.statuses?.find { status ->
            status.isDetailed
        }
    }

    fun reblog(reblog: Boolean, status: StatusViewData.Concrete): Job = viewModelScope.launch {
        try {
            timelineCases.reblog(status.actionableId, reblog).await()
        } catch (t: Exception) {
            ifExpected(t) {
                Log.d(TAG, "Failed to reblog status " + status.actionableId, t)
            }
        }
    }

    fun favorite(favorite: Boolean, status: StatusViewData.Concrete): Job = viewModelScope.launch {
        try {
            timelineCases.favourite(status.actionableId, favorite).await()
        } catch (t: Exception) {
            ifExpected(t) {
                Log.d(TAG, "Failed to favourite status " + status.actionableId, t)
            }
        }
    }

    fun bookmark(bookmark: Boolean, status: StatusViewData.Concrete): Job = viewModelScope.launch {
        try {
            timelineCases.bookmark(status.actionableId, bookmark).await()
        } catch (t: Exception) {
            ifExpected(t) {
                Log.d(TAG, "Failed to favourite status " + status.actionableId, t)
            }
        }
    }

    fun voteInPoll(choices: List<Int>, status: StatusViewData.Concrete): Job = viewModelScope.launch {
        val poll = status.status.actionableStatus.poll ?: run {
            Log.w(TAG, "No poll on status ${status.id}")
            return@launch
        }

        val votedPoll = poll.votedCopy(choices)
        updateStatus(status.id) { status ->
            status.copy(poll = votedPoll)
        }

        try {
            timelineCases.voteInPoll(status.actionableId, poll.id, choices).await()
        } catch (t: Exception) {
            ifExpected(t) {
                Log.d(TAG, "Failed to vote in poll: " + status.actionableId, t)
            }
        }
    }

    fun removeStatus(statusToRemove: StatusViewData.Concrete) {
        updateSuccess { uiState ->
            uiState.copy(
                statuses = uiState.statuses.filterNot { status -> status == statusToRemove }
            )
        }
    }

    fun changeExpanded(expanded: Boolean, status: StatusViewData.Concrete) {
        updateSuccess { uiState ->
            val statuses = uiState.statuses.map { viewData ->
                if (viewData.id == status.id) {
                    viewData.copy(isExpanded = expanded)
                } else {
                    viewData
                }
            }
            uiState.copy(
                statuses = statuses,
                revealButton = statuses.getRevealButtonState()
            )
        }
    }

    fun changeContentShowing(isShowing: Boolean, status: StatusViewData.Concrete) {
        updateStatusViewData(status.id) { viewData ->
            viewData.copy(isShowingContent = isShowing)
        }
    }

    fun changeContentCollapsed(isCollapsed: Boolean, status: StatusViewData.Concrete) {
        updateStatusViewData(status.id) { viewData ->
            viewData.copy(isCollapsed = isCollapsed)
        }
    }

    private fun handleFavEvent(event: FavoriteEvent) {
        updateStatus(event.statusId) { status ->
            status.copy(favourited = event.favourite)
        }
    }

    private fun handleReblogEvent(event: ReblogEvent) {
        updateStatus(event.statusId) { status ->
            status.copy(reblogged = event.reblog)
        }
    }

    private fun handleBookmarkEvent(event: BookmarkEvent) {
        updateStatus(event.statusId) { status ->
            status.copy(bookmarked = event.bookmark)
        }
    }

    private fun handlePinEvent(event: PinEvent) {
        updateStatus(event.statusId) { status ->
            status.copy(pinned = event.pinned)
        }
    }

    private fun removeAllByAccountId(accountId: String) {
        updateSuccess { uiState ->
            uiState.copy(
                statuses = uiState.statuses.filter { viewData ->
                    viewData.status.account.id != accountId
                }
            )
        }
    }

    private fun handleStatusComposedEvent(event: StatusComposedEvent) {
        val eventStatus = event.status
        updateSuccess { uiState ->
            val statuses = uiState.statuses
            val detailedIndex = statuses.indexOfFirst { status -> status.isDetailed }
            val repliedIndex = statuses.indexOfFirst { status -> eventStatus.inReplyToId == status.id }
            if (detailedIndex != -1 && repliedIndex >= detailedIndex) {
                // there is a new reply to the detailed status or below -> display it
                val newStatuses = statuses.subList(0, repliedIndex + 1) +
                    eventStatus.toViewData() +
                    statuses.subList(repliedIndex + 1, statuses.size)
                uiState.copy(statuses = newStatuses)
            } else {
                uiState
            }
        }
    }

    private fun handleStatusDeletedEvent(event: StatusDeletedEvent) {
        updateSuccess { uiState ->
            uiState.copy(
                statuses = uiState.statuses.filter { status ->
                    status.id != event.statusId
                }
            )
        }
    }

    fun toggleRevealButton() {
        updateSuccess { uiState ->
            when (uiState.revealButton) {
                RevealButtonState.HIDE -> uiState.copy(
                    statuses = uiState.statuses.map { viewData ->
                        viewData.copy(isExpanded = false)
                    },
                    revealButton = RevealButtonState.REVEAL
                )
                RevealButtonState.REVEAL -> uiState.copy(
                    statuses = uiState.statuses.map { viewData ->
                        viewData.copy(isExpanded = true)
                    },
                    revealButton = RevealButtonState.HIDE
                )
                else -> uiState
            }
        }
    }

    private fun List<StatusViewData.Concrete>.getRevealButtonState(): RevealButtonState {
        val hasWarnings = any { viewData ->
            viewData.status.spoilerText.isNotEmpty()
        }

        return if (hasWarnings) {
            val allExpanded = none { viewData ->
                !viewData.isExpanded
            }
            if (allExpanded) {
                RevealButtonState.HIDE
            } else {
                RevealButtonState.REVEAL
            }
        } else {
            RevealButtonState.NO_BUTTON
        }
    }

    private fun loadFilters() {
        viewModelScope.launch {
            val filters = api.getFilters().getOrElse {
                Log.w(TAG, "Failed to fetch filters", it)
                return@launch
            }

            filterModel.initWithFilters(
                filters.filter { filter ->
                    filter.context.contains(Filter.THREAD)
                }
            )

            updateSuccess { uiState ->
                val statuses = uiState.statuses.filter()
                uiState.copy(
                    statuses = statuses,
                    revealButton = statuses.getRevealButtonState()
                )
            }
        }
    }

    private fun List<StatusViewData.Concrete>.filter(): List<StatusViewData.Concrete> {
        return filter { status ->
            status.isDetailed || !filterModel.shouldFilterStatus(status.status)
        }
    }

    private fun Status.toViewData(detailed: Boolean = false): StatusViewData.Concrete {
        val oldStatus = (_uiState.value as? ThreadUiState.Success)?.statuses?.find { it.id == this.id }
        return toViewData(
            isShowingContent = oldStatus?.isShowingContent ?: (alwaysShowSensitiveMedia || !actionableStatus.sensitive),
            isExpanded = oldStatus?.isExpanded ?: alwaysOpenSpoiler,
            isCollapsed = oldStatus?.isCollapsed ?: !detailed,
            isDetailed = oldStatus?.isDetailed ?: detailed
        )
    }

    private inline fun updateSuccess(updater: (ThreadUiState.Success) -> ThreadUiState.Success) {
        _uiState.update { uiState ->
            if (uiState is ThreadUiState.Success) {
                updater(uiState)
            } else {
                uiState
            }
        }
    }

    private fun updateStatusViewData(statusId: String, updater: (StatusViewData.Concrete) -> StatusViewData.Concrete) {
        updateSuccess { uiState ->
            uiState.copy(
                statuses = uiState.statuses.map { viewData ->
                    if (viewData.id == statusId) {
                        updater(viewData)
                    } else {
                        viewData
                    }
                }
            )
        }
    }

    private fun updateStatus(statusId: String, updater: (Status) -> Status) {
        updateStatusViewData(statusId) { viewData ->
            viewData.copy(
                status = updater(viewData.status)
            )
        }
    }

    companion object {
        private const val TAG = "ViewThreadViewModel"
    }
}

sealed interface ThreadUiState {
    object Loading : ThreadUiState
    class Error(val throwable: Throwable) : ThreadUiState
    data class Success(
        val statuses: List<StatusViewData.Concrete>,
        val revealButton: RevealButtonState,
        val refreshing: Boolean
    ) : ThreadUiState
}

enum class RevealButtonState {
    NO_BUTTON, REVEAL, HIDE
}
