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

package com.keylesspalace.tusky.components.timeline.viewmodel

import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.CallSuper
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import at.connyduck.calladapter.networkresult.getOrThrow
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.appstore.BlockEvent
import com.keylesspalace.tusky.appstore.BookmarkEvent
import com.keylesspalace.tusky.appstore.DomainMuteEvent
import com.keylesspalace.tusky.appstore.Event
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.FavoriteEvent
import com.keylesspalace.tusky.appstore.MuteConversationEvent
import com.keylesspalace.tusky.appstore.MuteEvent
import com.keylesspalace.tusky.appstore.PinEvent
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.appstore.ReblogEvent
import com.keylesspalace.tusky.appstore.StatusComposedEvent
import com.keylesspalace.tusky.appstore.StatusDeletedEvent
import com.keylesspalace.tusky.appstore.StatusEditedEvent
import com.keylesspalace.tusky.appstore.UnfollowEvent
import com.keylesspalace.tusky.components.timeline.FilterKind
import com.keylesspalace.tusky.components.timeline.FiltersRepository
import com.keylesspalace.tusky.components.timeline.TimelineKind
import com.keylesspalace.tusky.components.timeline.util.ifExpected
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.FilterV1
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.settings.AccountPreferenceDataStore
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.usecase.TimelineCases
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.throttleFirst
import com.keylesspalace.tusky.viewdata.StatusViewData
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

data class UiState(
    /** True if the FAB should be shown while scrolling */
    val showFabWhileScrolling: Boolean = true,

    /** True if media previews should be shown */
    val showMediaPreview: Boolean = true
)

/** Preferences the UI reacts to */
data class UiPrefs(
    val showFabWhileScrolling: Boolean,
    val showMediaPreview: Boolean
) {
    companion object {
        /** Relevant preference keys. Changes to any of these trigger a display update */
        val prefKeys = setOf(
            PrefKeys.FAB_HIDE,
            PrefKeys.MEDIA_PREVIEW_ENABLED
        )
    }
}

// TODO: Ui* classes are copied from NotificationsViewModel. Not yet sure whether these actions
// are "global" across all timelines (including notifications) or whether notifications are
// sufficiently different to warrant having a duplicate set. Keeping them duplicated for the
// moment.

/** Parent class for all UI actions, fallible or infallible. */
sealed class UiAction

/** Actions the user can trigger from the UI. These actions may fail. */
sealed class FallibleUiAction : UiAction() {
    /* none at the moment */
}

/**
 * Actions the user can trigger from the UI that either cannot fail, or if they do fail,
 * do not show an error.
 */
sealed class InfallibleUiAction : UiAction() {
    /**
     * User is leaving the fragment, save the ID of the visible status.
     *
     * Infallible because if it fails there's nowhere to show the error, and nothing the user
     * can do.
     */
    data class SaveVisibleId(val visibleId: String) : InfallibleUiAction()

    /** Ignore the saved reading position, load the page with the newest items */
    // Resets the account's reading position, which can't fail, which is why this is
    // infallible. Reloading the data may fail, but that's handled by the paging system /
    // adapter refresh logic.
    object LoadNewest : InfallibleUiAction()
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

    /** A status the user wrote was successfully posted */
    data class StatusSent(val status: Status) : UiSuccess()

    /** A status the user wrote was successfully edited */
    data class StatusEdited(val status: Status) : UiSuccess()
}

/** Actions the user can trigger on an individual status */
sealed class StatusAction(open val statusViewData: StatusViewData) : FallibleUiAction() {
    /** Set the bookmark state for a status */
    data class Bookmark(val state: Boolean, override val statusViewData: StatusViewData) :
        StatusAction(statusViewData)

    /** Set the favourite state for a status */
    data class Favourite(val state: Boolean, override val statusViewData: StatusViewData) :
        StatusAction(statusViewData)

    /** Set the reblog state for a status */
    data class Reblog(val state: Boolean, override val statusViewData: StatusViewData) :
        StatusAction(statusViewData)

    /** Vote in a poll */
    data class VoteInPoll(
        val poll: Poll,
        val choices: List<Int>,
        override val statusViewData: StatusViewData
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

// TODO: Similar to UiError in NotificationsViewModel, but without ClearNotifications,
// AcceptFollowRequest, and RejectFollowRequest.
//
// Possibly indicates the need for UiStatusError and UiNotificationError subclasses so these
// can be shared.
//
// Need to think about how that would work with sealed classes.

/** Errors from fallible view model actions that the UI will need to show */
sealed class UiError(
    /** The throwable associated with the error */
    open val throwable: Throwable,

    /** String resource with an error message to show the user */
    @StringRes val message: Int,

    /** The action that failed. Can be resent to retry the action */
    open val action: UiAction? = null
) {
    data class Bookmark(
        override val throwable: Throwable,
        override val action: StatusAction.Bookmark
    ) : UiError(throwable, R.string.ui_error_bookmark, action)

    data class Favourite(
        override val throwable: Throwable,
        override val action: StatusAction.Favourite
    ) : UiError(throwable, R.string.ui_error_favourite, action)

    data class Reblog(
        override val throwable: Throwable,
        override val action: StatusAction.Reblog
    ) : UiError(throwable, R.string.ui_error_reblog, action)

    data class VoteInPoll(
        override val throwable: Throwable,
        override val action: StatusAction.VoteInPoll
    ) : UiError(throwable, R.string.ui_error_vote, action)

    data class GetFilters(
        override val throwable: Throwable
    ) : UiError(throwable, R.string.ui_error_filter_v1_load, null)

    companion object {
        fun make(throwable: Throwable, action: FallibleUiAction) = when (action) {
            is StatusAction.Bookmark -> Bookmark(throwable, action)
            is StatusAction.Favourite -> Favourite(throwable, action)
            is StatusAction.Reblog -> Reblog(throwable, action)
            is StatusAction.VoteInPoll -> VoteInPoll(throwable, action)
        }
    }
}

@OptIn(FlowPreview::class, ExperimentalTime::class)
abstract class TimelineViewModel(
    private val timelineCases: TimelineCases,
    private val eventHub: EventHub,
    private val filtersRepository: FiltersRepository,
    protected val accountManager: AccountManager,
    private val sharedPreferences: SharedPreferences,
    private val accountPreferenceDataStore: AccountPreferenceDataStore,
    private val filterModel: FilterModel
) : ViewModel() {
    val uiState: StateFlow<UiState>

    abstract val statuses: Flow<PagingData<StatusViewData>>

    /** Flow of changes to statusDisplayOptions, for use by the UI */
    val statusDisplayOptions: StateFlow<StatusDisplayOptions>

    /** Flow of user actions received from the UI */
    private val uiAction = MutableSharedFlow<UiAction>()

    /** Flow that can be used to trigger a full reload */
    protected val reload = MutableStateFlow(0)

    /** Flow of successful action results */
    // Note: Thisis a SharedFlow instead of a StateFlow because success state does not need to be
    // retained. A message is shown once to a user and then dismissed. Re-collecting the flow
    // (e.g., after a device orientation change) should not re-show the most recent success
    // message, as it will be confusing to the user.
    val uiSuccess = MutableSharedFlow<UiSuccess>()

    /** Channel for error results */
    // Errors are sent to a channel to ensure that any errors that occur *before* there are any
    // subscribers are retained. If this was a SharedFlow any errors would be dropped, and if it
    // was a StateFlow any errors would be retained, and there would need to be an explicit
    // mechanism to dismiss them.
    private val _uiErrorChannel = Channel<UiError>()

    /** Expose UI errors as a flow */
    val uiError = _uiErrorChannel.receiveAsFlow()

    /** Accept UI actions in to actionStateFlow */
    val accept: (UiAction) -> Unit = { action ->
        viewModelScope.launch { uiAction.emit(action) }
    }

    var timelineKind: TimelineKind = TimelineKind.Home
        private set

    private var filterRemoveReplies = false
    private var filterRemoveReblogs = false

    protected val activeAccount = accountManager.activeAccount!!

    /** The ID of the status to which the user's reading position should be restored */
    // Not part of the UiState as it's only used once in the lifespan of the fragment.
    // Subclasses should set this if they support restoring the reading position.
    open var readingPositionId: String? = null
        protected set

    init {
        viewModelScope.launch {
            updateFiltersFromPreferences().collectLatest {
                Log.d(TAG, "Filters updated")
            }
        }

        // Set initial status display options from the user's preferences.
        //
        // Then collect future preference changes and emit new values in to
        // statusDisplayOptions if necessary.
        statusDisplayOptions = MutableStateFlow(
            StatusDisplayOptions.from(
                sharedPreferences,
                activeAccount
            )
        )

        viewModelScope.launch {
            eventHub.events
                .filterIsInstance<PreferenceChangedEvent>()
                .filter { StatusDisplayOptions.prefKeys.contains(it.preferenceKey) }
                .map {
                    statusDisplayOptions.value.make(
                        sharedPreferences,
                        it.preferenceKey,
                        activeAccount
                    )
                }
                .collect {
                    statusDisplayOptions.emit(it)
                }
        }

        // Handle StatusAction.*
        viewModelScope.launch {
            uiAction.filterIsInstance<StatusAction>()
                .throttleFirst(THROTTLE_TIMEOUT) // avoid double-taps
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
                        }.getOrThrow()
                        uiSuccess.emit(StatusActionSuccess.from(action))
                    } catch (e: Exception) {
                        ifExpected(e) { _uiErrorChannel.send(UiError.make(e, action)) }
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
                    is StatusComposedEvent -> uiSuccess.emit(UiSuccess.StatusSent(it.status))
                    is StatusEditedEvent -> uiSuccess.emit(UiSuccess.StatusEdited(it.status))
                }
            }
        }

        uiState = getUiPrefs().map { prefs ->
            UiState(
                showFabWhileScrolling = prefs.showFabWhileScrolling,
                showMediaPreview = prefs.showMediaPreview
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
            initialValue = UiState()
        )
    }

    /**
     * @return Flow of relevant preferences that change the UI
     */
    // TODO: Preferences should be in a repository
    protected fun getUiPrefs() = eventHub.events
        .filterIsInstance<PreferenceChangedEvent>()
        .onEach { println("PreferenceChangedEvent: $it") }
        .filter { UiPrefs.prefKeys.contains(it.preferenceKey) }
        .map { toPrefs() }
        .onStart { emit(toPrefs()) }

    private fun toPrefs() = UiPrefs(
        showFabWhileScrolling = !sharedPreferences.getBoolean(PrefKeys.FAB_HIDE, false),
        showMediaPreview = accountPreferenceDataStore.getBoolean(PrefKeys.MEDIA_PREVIEW_ENABLED, true)
    )

    @CallSuper
    open fun init(timelineKind: TimelineKind) {
        this.timelineKind = timelineKind

        filterModel.kind = Filter.Kind.from(timelineKind)

        if (timelineKind is TimelineKind.Home) {
            // Note the variable is "true if filter" but the underlying preference/settings text is "true if show"
            filterRemoveReplies =
                !sharedPreferences.getBoolean(PrefKeys.TAB_FILTER_HOME_REPLIES, true)
            filterRemoveReblogs =
                !sharedPreferences.getBoolean(PrefKeys.TAB_FILTER_HOME_BOOSTS, true)
        }

        // Save the visible status ID (if it's the home timeline)
        if (timelineKind == TimelineKind.Home) {
            viewModelScope.launch {
                uiAction
                    .filterIsInstance<InfallibleUiAction.SaveVisibleId>()
                    .distinctUntilChanged()
                    .collectLatest { action ->
                        Log.d(TAG, "Saving Home timeline position at: ${action.visibleId}")
                        activeAccount.lastVisibleHomeTimelineStatusId = action.visibleId
                        accountManager.saveAccount(activeAccount)
                    }
            }
        }

        // Clear the saved visible ID (if necessary), and reload from the newest status.
        viewModelScope.launch {
            uiAction
                .filterIsInstance<InfallibleUiAction.LoadNewest>()
                .collectLatest {
                    if (timelineKind == TimelineKind.Home) {
                        activeAccount.lastVisibleHomeTimelineStatusId = null
                        accountManager.saveAccount(activeAccount)
                    }
                    reloadFromNewest()
                }
        }

        viewModelScope.launch {
            eventHub.events
                .filterIsInstance<PreferenceChangedEvent>()
                .filter { FilterPrefs.contains(it.preferenceKey) }
                .distinctUntilChanged()
                .map { getFilters() }
                .onStart { getFilters() }
        }

        viewModelScope.launch {
            eventHub.events
                .collect { event -> handleEvent(event) }
        }
    }

    fun getInitialKey(): String? {
        if (timelineKind != TimelineKind.Home) {
            return null
        }

        return activeAccount.lastVisibleHomeTimelineStatusId
    }

    abstract fun updatePoll(newPoll: Poll, status: StatusViewData)

    abstract fun changeExpanded(expanded: Boolean, status: StatusViewData)

    abstract fun changeContentShowing(isShowing: Boolean, status: StatusViewData)

    abstract fun changeContentCollapsed(isCollapsed: Boolean, status: StatusViewData)

    abstract fun removeAllByAccountId(accountId: String)

    abstract fun removeAllByInstance(instance: String)

    abstract fun removeStatusWithId(id: String)

    abstract fun handleReblogEvent(reblogEvent: ReblogEvent)

    abstract fun handleFavEvent(favEvent: FavoriteEvent)

    abstract fun handleBookmarkEvent(bookmarkEvent: BookmarkEvent)

    abstract fun handlePinEvent(pinEvent: PinEvent)

    /**
     * Reload data for this timeline while preserving the user's reading position.
     *
     * Subclasses should call this, then start loading data.
     */
    @CallSuper
    open fun reloadKeepingReadingPosition() {
        reload.getAndUpdate { it + 1 }
    }

    /**
     * Load the most recent data for this timeline, ignoring the user's reading position.
     *
     * Subclasses should call this, then start loading data.
     */
    @CallSuper
    open fun reloadFromNewest() {
        reload.getAndUpdate { it + 1 }
    }

    abstract fun clearWarning(status: StatusViewData)

    /** Triggered when currently displayed data must be reloaded. */
    protected abstract suspend fun invalidate()

    protected fun shouldFilterStatus(statusViewData: StatusViewData): Filter.Action {
        val status = statusViewData.status
        return if (
            (status.inReplyToId != null && filterRemoveReplies) ||
            (status.reblog != null && filterRemoveReblogs)
        ) {
            return Filter.Action.HIDE
        } else {
            statusViewData.filterAction = filterModel.shouldFilterStatus(status.actionableStatus)
            statusViewData.filterAction
        }
    }

    private val FilterPrefs = setOf(
        FilterV1.HOME,
        FilterV1.NOTIFICATIONS,
        FilterV1.THREAD,
        FilterV1.PUBLIC,
        FilterV1.ACCOUNT
    )

    /** Updates the current set of filters if filter-related preferences change */
    // TODO: https://github.com/tuskyapp/Tusky/issues/3546, and update if a v2 filter is
    // updated as well.
    private fun updateFiltersFromPreferences() = eventHub.events
        .filterIsInstance<PreferenceChangedEvent>()
        .filter { FilterPrefs.contains(it.preferenceKey) }
        .filter { filterContextMatchesKind(timelineKind, listOf(it.preferenceKey)) }
        .distinctUntilChanged()
        .map { getFilters() }
        .onStart { getFilters() }

    /**
     * Gets the current filters from the repository. Applies them locally if they are
     * v1 filters.
     *
     * Whatever the filter kind, the current timeline is invalidated, so it updates with the
     * most recent filters.
     */
    private fun getFilters() {
        viewModelScope.launch {
            Log.d(TAG, "getFilters()")
            try {
                when (val filters = filtersRepository.getFilters()) {
                    is FilterKind.V1 -> {
                        filterModel.initWithFilters(
                            filters.filters.filter {
                                filterContextMatchesKind(timelineKind, it.context)
                            }
                        )
                        invalidate()
                    }

                    is FilterKind.V2 -> invalidate()
                }
            } catch (throwable: Throwable) {
                Log.d(TAG, "updateFilter(): Error fetching filters: ${throwable.message}")
                _uiErrorChannel.send(UiError.GetFilters(throwable))
            }
        }
    }

    // TODO: Update this so that the list of UIPrefs is correct
    private fun onPreferenceChanged(key: String) {
        when (key) {
            PrefKeys.TAB_FILTER_HOME_REPLIES -> {
                val filter = sharedPreferences.getBoolean(PrefKeys.TAB_FILTER_HOME_REPLIES, true)
                val oldRemoveReplies = filterRemoveReplies
                filterRemoveReplies = timelineKind is TimelineKind.Home && !filter
                if (oldRemoveReplies != filterRemoveReplies) {
                    reloadKeepingReadingPosition()
                }
            }
            PrefKeys.TAB_FILTER_HOME_BOOSTS -> {
                val filter = sharedPreferences.getBoolean(PrefKeys.TAB_FILTER_HOME_BOOSTS, true)
                val oldRemoveReblogs = filterRemoveReblogs
                filterRemoveReblogs = timelineKind is TimelineKind.Home && !filter
                if (oldRemoveReblogs != filterRemoveReblogs) {
                    reloadKeepingReadingPosition()
                }
            }
        }
    }

    private fun handleEvent(event: Event) {
        when (event) {
            is FavoriteEvent -> handleFavEvent(event)
            is ReblogEvent -> handleReblogEvent(event)
            is BookmarkEvent -> handleBookmarkEvent(event)
            is PinEvent -> handlePinEvent(event)
            is MuteConversationEvent -> reloadKeepingReadingPosition()
            is UnfollowEvent -> {
                if (timelineKind is TimelineKind.Home) {
                    val id = event.accountId
                    removeAllByAccountId(id)
                }
            }
            is BlockEvent -> {
                if (timelineKind !is TimelineKind.User) {
                    val id = event.accountId
                    removeAllByAccountId(id)
                }
            }
            is MuteEvent -> {
                if (timelineKind !is TimelineKind.User) {
                    val id = event.accountId
                    removeAllByAccountId(id)
                }
            }
            is DomainMuteEvent -> {
                if (timelineKind !is TimelineKind.User) {
                    val instance = event.instance
                    removeAllByInstance(instance)
                }
            }
            is StatusDeletedEvent -> {
                if (timelineKind !is TimelineKind.User) {
                    removeStatusWithId(event.statusId)
                }
            }
            is PreferenceChangedEvent -> {
                onPreferenceChanged(event.preferenceKey)
            }
        }
    }

    companion object {
        private const val TAG = "TimelineViewModel"
        private val THROTTLE_TIMEOUT = 500.milliseconds

        fun filterContextMatchesKind(
            timelineKind: TimelineKind,
            filterContext: List<String>
        ): Boolean {
            return filterContext.contains(Filter.Kind.from(timelineKind).kind)
        }
    }
}
