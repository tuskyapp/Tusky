/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.components.notifications

import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.appstore.BlockEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.MuteConversationEvent
import com.keylesspalace.tusky.appstore.MuteEvent
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.components.timeline.util.ifExpected
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.usecase.TimelineCases
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.deserialize
import com.keylesspalace.tusky.util.serialize
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.viewdata.NotificationViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import retrofit2.HttpException
import javax.inject.Inject

data class UiState(
    /** Filtered notification types */
    val activeFilter: Set<Notification.Type> = emptySet(),

    /** True if the UI to filter and clear notifications should be shown */
    val showFilterOptions: Boolean = false,

    /** True if the FAB should be shown while scrolling */
    val showFabWhileScrolling: Boolean = true
)

/** Preferences the UI reacts to */
data class UiPrefs(
    val showFabWhileScrolling: Boolean,
    val showFilter: Boolean
) {
    companion object {
        /** Relevant preference keys. Changes to any of these trigger a display update */
        val prefKeys = setOf(
            PrefKeys.FAB_HIDE,
            PrefKeys.SHOW_NOTIFICATIONS_FILTER
        )
    }
}

/** Parent class for all UI actions, fallible or infallible. */
sealed class UiAction

/** Actions the user can trigger from the UI. These actions may fail. */
sealed class FallibleUiAction : UiAction() {
    /** Clear all notifications */
    object ClearNotifications : FallibleUiAction()
}

/**
 * Actions the user can trigger from the UI that either cannot fail, or if they do fail,
 * do not show an error.
 */
sealed class InfallibleUiAction : UiAction() {
    /** Apply a new filter to the notification list */
    // This saves the list to the local database, which triggers a refresh of the data.
    // Saving the data can't fail, which is why this is infallible. Refreshing the
    // data may fail, but that's handled by the paging system / adapter refresh logic.
    data class ApplyFilter(val filter: Set<Notification.Type>) : InfallibleUiAction()

    /**
     * User is leaving the fragment, save the ID of the visible notification.
     *
     * Infallible because if it fails there's nowhere to show the error, and nothing the user
     * can do.
     */
    data class SaveVisibleId(val visibleId: String) : InfallibleUiAction()
}

/** Actions the user can trigger on an individual notification. These may fail. */
sealed class NotificationAction : FallibleUiAction() {
    data class AcceptFollowRequest(val accountId: String) : NotificationAction()

    data class RejectFollowRequest(val accountId: String) : NotificationAction()
}

sealed class UiSuccess {
    // These three are from menu items on the status. Currently they don't come to the
    // viewModel as actions, they're noticed when events are posted. That will change,
    // but for the moment we can still report them to the UI. Typically, receiving any
    // of these three should trigger the UI to refresh.

    /** A user was blocked */
    object Block : UiSuccess()

    /** A user was muted */
    object Mute : UiSuccess()

    /** A conversation was muted */
    object MuteConversation : UiSuccess()
}

/** The result of a successful action on a notification */
sealed class NotificationActionSuccess(
    /** String resource with an error message to show the user */
    @StringRes val msg: Int,

    /**
     * The original action, in case additional information is required from it to display the
     * message.
     */
    open val action: NotificationAction
) : UiSuccess() {
    data class AcceptFollowRequest(override val action: NotificationAction) :
        NotificationActionSuccess(R.string.ui_success_accepted_follow_request, action)
    data class RejectFollowRequest(override val action: NotificationAction) :
        NotificationActionSuccess(R.string.ui_success_rejected_follow_request, action)

    companion object {
        fun from(action: NotificationAction) = when (action) {
            is NotificationAction.AcceptFollowRequest -> AcceptFollowRequest(action)
            is NotificationAction.RejectFollowRequest -> RejectFollowRequest(action)
        }
    }
}

/** Actions the user can trigger on an individual status */
sealed class StatusAction(
    open val statusViewData: StatusViewData.Concrete
) : FallibleUiAction() {
    /** Set the bookmark state for a status */
    data class Bookmark(val state: Boolean, override val statusViewData: StatusViewData.Concrete) :
        StatusAction(statusViewData)

    /** Set the favourite state for a status */
    data class Favourite(val state: Boolean, override val statusViewData: StatusViewData.Concrete) :
        StatusAction(statusViewData)

    /** Set the reblog state for a status */
    data class Reblog(val state: Boolean, override val statusViewData: StatusViewData.Concrete) :
        StatusAction(statusViewData)

    /** Vote in a poll */
    data class VoteInPoll(
        val poll: Poll,
        val choices: List<Int>,
        override val statusViewData: StatusViewData.Concrete
    ) : StatusAction(statusViewData)
}

/** Changes to a status' visible state after API calls */
sealed class StatusActionSuccess(open val action: StatusAction) : UiSuccess() {
    data class Bookmark(override val action: StatusAction.Bookmark) :
        StatusActionSuccess(action)

    data class Favourite(override val action: StatusAction.Favourite) :
        StatusActionSuccess(action)

    data class Reblog(override val action: StatusAction.Reblog) :
        StatusActionSuccess(action)

    data class VoteInPoll(override val action: StatusAction.VoteInPoll) :
        StatusActionSuccess(action)

    companion object {
        fun from(action: StatusAction) = when (action) {
            is StatusAction.Bookmark -> Bookmark(action)
            is StatusAction.Favourite -> Favourite(action)
            is StatusAction.Reblog -> Reblog(action)
            is StatusAction.VoteInPoll -> VoteInPoll(action)
        }
    }
}

/** Errors from fallible view model actions that the UI will need to show */
sealed class UiError(
    /** The exception associated with the error */
    open val exception: Exception,

    /** String resource with an error message to show the user */
    @StringRes val message: Int,

    /** The action that failed. Can be resent to retry the action */
    open val action: UiAction? = null
) {
    data class ClearNotifications(override val exception: Exception) : UiError(
        exception,
        R.string.ui_error_clear_notifications
    )

    data class Bookmark(
        override val exception: Exception,
        override val action: StatusAction.Bookmark
    ) : UiError(exception, R.string.ui_error_bookmark, action)

    data class Favourite(
        override val exception: Exception,
        override val action: StatusAction.Favourite
    ) : UiError(exception, R.string.ui_error_favourite, action)

    data class Reblog(
        override val exception: Exception,
        override val action: StatusAction.Reblog
    ) : UiError(exception, R.string.ui_error_reblog, action)

    data class VoteInPoll(
        override val exception: Exception,
        override val action: StatusAction.VoteInPoll
    ) : UiError(exception, R.string.ui_error_vote, action)

    data class AcceptFollowRequest(
        override val exception: Exception,
        override val action: NotificationAction.AcceptFollowRequest
    ) : UiError(exception, R.string.ui_error_accept_follow_request, action)

    data class RejectFollowRequest(
        override val exception: Exception,
        override val action: NotificationAction.RejectFollowRequest
    ) : UiError(exception, R.string.ui_error_reject_follow_request, action)

    companion object {
        fun make(exception: Exception, action: FallibleUiAction) = when (action) {
            is StatusAction.Bookmark -> Bookmark(exception, action)
            is StatusAction.Favourite -> Favourite(exception, action)
            is StatusAction.Reblog -> Reblog(exception, action)
            is StatusAction.VoteInPoll -> VoteInPoll(exception, action)
            is NotificationAction.AcceptFollowRequest -> AcceptFollowRequest(exception, action)
            is NotificationAction.RejectFollowRequest -> RejectFollowRequest(exception, action)
            FallibleUiAction.ClearNotifications -> ClearNotifications(exception)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationsRepository,
    private val preferences: SharedPreferences,
    private val accountManager: AccountManager,
    private val timelineCases: TimelineCases,
    private val eventHub: EventHub
) : ViewModel() {
    /** The account to display notifications for */
    val account = accountManager.activeAccount!!

    val uiState: StateFlow<UiState>

    /** Flow of changes to statusDisplayOptions, for use by the UI */
    val statusDisplayOptions: StateFlow<StatusDisplayOptions>

    val pagingData: Flow<PagingData<NotificationViewData>>

    /** Flow of user actions received from the UI */
    private val uiAction = MutableSharedFlow<UiAction>()

    /** Flow of successful action results */
    // Note: These are a SharedFlow instead of a StateFlow because success or error state does not
    // need to be retained. A message is shown once to a user and then dismissed. Re-collecting the
    // flow (e.g., after a device orientation change) should not re-show the most recent success or
    // error message, as it will be confusing to the user.
    val uiSuccess = MutableSharedFlow<UiSuccess>()

    /** Flow of transient errors for the UI to present */
    val uiError = MutableSharedFlow<UiError>()

    /** Accept UI actions in to actionStateFlow */
    val accept: (UiAction) -> Unit = { action ->
        viewModelScope.launch { uiAction.emit(action) }
    }

    init {
        // Handle changes to notification filters
        val notificationFilter = uiAction
            .filterIsInstance<InfallibleUiAction.ApplyFilter>()
            .distinctUntilChanged()
            // Save each change back to the active account
            .onEach { action ->
                Log.d(TAG, "notificationFilter: $action")
                account.notificationsFilter = serialize(action.filter)
                accountManager.saveAccount(account)
            }
            // Load the initial filter from the active account
            .onStart {
                emit(
                    InfallibleUiAction.ApplyFilter(
                        filter = deserialize(account.notificationsFilter)
                    )
                )
            }

        // Save the visible notification ID
        viewModelScope.launch {
            uiAction
                .filterIsInstance<InfallibleUiAction.SaveVisibleId>()
                .distinctUntilChanged()
                .collectLatest { action ->
                    Log.d(TAG, "Saving visible ID: ${action.visibleId}, active account = ${account.id}")
                    account.lastNotificationId = action.visibleId
                    accountManager.saveAccount(account)
                }
        }

        // Set initial status display options from the user's preferences.
        //
        // Then collect future preference changes and emit new values in to
        // statusDisplayOptions if necessary.
        statusDisplayOptions = MutableStateFlow(
            StatusDisplayOptions.from(
                preferences,
                account
            )
        )

        viewModelScope.launch {
            eventHub.events
                .filterIsInstance<PreferenceChangedEvent>()
                .filter { StatusDisplayOptions.prefKeys.contains(it.preferenceKey) }
                .map {
                    statusDisplayOptions.value.make(
                        preferences,
                        it.preferenceKey,
                        account
                    )
                }
                .collect {
                    statusDisplayOptions.emit(it)
                }
        }

        // Handle UiAction.ClearNotifications
        viewModelScope.launch {
            uiAction.filterIsInstance<FallibleUiAction.ClearNotifications>()
                .collectLatest {
                    try {
                        repository.clearNotifications().apply {
                            if (this.isSuccessful) {
                                repository.invalidate()
                            } else {
                                uiError.emit(UiError.make(HttpException(this), it))
                            }
                        }
                    } catch (e: Exception) {
                        ifExpected(e) { uiError.emit(UiError.make(e, it)) }
                    }
                }
        }

        // Handle NotificationAction.*
        viewModelScope.launch {
            uiAction.filterIsInstance<NotificationAction>()
                .debounce(DEBOUNCE_TIMEOUT_MS)
                .collect { action ->
                    try {
                        when (action) {
                            is NotificationAction.AcceptFollowRequest ->
                                timelineCases.acceptFollowRequest(action.accountId).await()
                            is NotificationAction.RejectFollowRequest ->
                                timelineCases.rejectFollowRequest(action.accountId).await()
                        }
                        uiSuccess.emit(NotificationActionSuccess.from(action))
                    } catch (e: Exception) {
                        ifExpected(e) { uiError.emit(UiError.make(e, action)) }
                    }
                }
        }

        // Handle StatusAction.*
        viewModelScope.launch {
            uiAction.filterIsInstance<StatusAction>()
                .debounce(DEBOUNCE_TIMEOUT_MS) // avoid double-taps
                .collect { action ->
                    try {
                        when (action) {
                            is StatusAction.Bookmark ->
                                timelineCases.bookmark(
                                    action.statusViewData.actionableId,
                                    action.state
                                )
                            is StatusAction.Favourite ->
                                timelineCases.favourite(
                                    action.statusViewData.actionableId,
                                    action.state
                                )
                            is StatusAction.Reblog ->
                                timelineCases.reblog(
                                    action.statusViewData.actionableId,
                                    action.state
                                )
                            is StatusAction.VoteInPoll ->
                                timelineCases.voteInPoll(
                                    action.statusViewData.actionableId,
                                    action.poll.id,
                                    action.choices
                                )
                        }
                        uiSuccess.emit(StatusActionSuccess.from(action))
                    } catch (e: Exception) {
                        ifExpected(e) { uiError.emit(UiError.make(e, action)) }
                    }
                }
        }

        // Handle events that should refresh the list
        viewModelScope.launch {
            eventHub.events.collectLatest {
                when (it) {
                    is BlockEvent -> uiSuccess.emit(UiSuccess.Block)
                    is MuteEvent -> uiSuccess.emit(UiSuccess.Mute)
                    is MuteConversationEvent -> uiSuccess.emit(UiSuccess.MuteConversation)
                }
            }
        }

        pagingData = notificationFilter
            .flatMapLatest { action ->
                getNotifications(filters = action.filter, initialKey = getInitialKey())
            }
            .cachedIn(viewModelScope)

        uiState = combine(notificationFilter, getUiPrefs()) { filter, prefs ->
            UiState(
                activeFilter = filter.filter,
                showFilterOptions = prefs.showFilter,
                showFabWhileScrolling = prefs.showFabWhileScrolling
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
            initialValue = UiState()
        )
    }

    private fun getNotifications(
        filters: Set<Notification.Type>,
        initialKey: String? = null
    ): Flow<PagingData<NotificationViewData>> {
        return repository.getNotificationsStream(filter = filters, initialKey = initialKey)
            .map { pagingData ->
                pagingData.map { notification ->
                    notification.toViewData(
                        isShowingContent = statusDisplayOptions.value.showSensitiveMedia ||
                            !(notification.status?.actionableStatus?.sensitive ?: false),
                        isExpanded = statusDisplayOptions.value.openSpoiler,
                        isCollapsed = true
                    )
                }
            }
    }

    // The database stores "0" as the last notification ID if notifications have not been
    // fetched. Convert to null to ensure a full fetch in this case
    private fun getInitialKey(): String? {
        val initialKey = when (val id = account.lastNotificationId) {
            "0" -> null
            else -> id
        }
        Log.d(TAG, "Restoring at $initialKey")
        return initialKey
    }

    /**
     * @return Flow of relevant preferences that change the UI
     */
    // TODO: Preferences should be in a repository
    private fun getUiPrefs() = eventHub.events
        .filterIsInstance<PreferenceChangedEvent>()
        .filter { UiPrefs.prefKeys.contains(it.preferenceKey) }
        .map { toPrefs() }
        .onStart { emit(toPrefs()) }

    private fun toPrefs() = UiPrefs(
        showFabWhileScrolling = !preferences.getBoolean(PrefKeys.FAB_HIDE, false),
        showFilter = preferences.getBoolean(PrefKeys.SHOW_NOTIFICATIONS_FILTER, true)
    )

    companion object {
        private const val TAG = "NotificationsViewModel"
        private const val DEBOUNCE_TIMEOUT_MS = 500L
    }
}
