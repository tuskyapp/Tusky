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
import com.keylesspalace.tusky.appstore.EventHub
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
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.rx3.await
import retrofit2.HttpException
import javax.inject.Inject

data class UiState(
    /** Filtered notification types */
    val activeFilter: Set<Notification.Type> = emptySet(),

    /** True if statuses should display absolute time */
    val showAbsoluteTime: Boolean = false,

    /** True if the UI to filter and clear notifications should be shown */
    val showFilterOptions: Boolean = false,

    /** True if the FAB should be shown while scrolling */
    val showFabWhileScrolling: Boolean = true
)

// TODO: The status functions this exposes (reblog, favourite, bookmark, etc) are very similar
// to those in TimelineViewModel. Investigate how to de-duplicate them where practical.

// TODO: Maybe view models should implement StatusActionListener? If the interface's methods
// included the status data as well as the position there's no need to go through the hosting
// fragment or activity to get the data. That would simplify the code slightly and save a function
// call.

/** Actions the user can trigger from the UI */
sealed class UiAction {
    /** Apply a new filter to the notification list */
    data class ApplyFilter(val filter: Set<Notification.Type>) : UiAction()

    /** User is leaving the fragment, save the ID of the visible notification */
    data class SaveVisibleId(val visibleId: String) : UiAction()

    /** Clear all notifications */
    object ClearNotifications : UiAction()
}

/** Actions the user can trigger on an individual status */
sealed class StatusAction(
    open val statusViewData: StatusViewData.Concrete
) : UiAction() {
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
sealed class StatusUiChange(open val statusViewData: StatusViewData) {
    data class Bookmark(val state: Boolean, override val statusViewData: StatusViewData.Concrete) :
        StatusUiChange(statusViewData)

    data class Favourite(val state: Boolean, override val statusViewData: StatusViewData.Concrete) :
        StatusUiChange(statusViewData)

    data class Reblog(val state: Boolean, override val statusViewData: StatusViewData.Concrete) :
        StatusUiChange(statusViewData)

    data class VoteInPoll(
        val poll: Poll,
        val choices: List<Int>,
        override val statusViewData: StatusViewData.Concrete
    ) : StatusUiChange(statusViewData)

    companion object {
        fun from(action: StatusAction) = when (action) {
            is StatusAction.Bookmark -> Bookmark(action.state, action.statusViewData)
            is StatusAction.Favourite -> Favourite(action.state, action.statusViewData)
            is StatusAction.Reblog -> Reblog(action.state, action.statusViewData)
            is StatusAction.VoteInPoll -> VoteInPoll(action.poll, action.choices, action.statusViewData)
        }
    }
}

/** Preferences the UI reacts to */
data class UiPrefs(
    val showAbsoluteTime: Boolean,
    val showFabWhileScrolling: Boolean,
    val showFilter: Boolean
) {
    companion object {
        /** Relevant preference keys. Changes to any of these trigger a display update */
        val prefKeys = setOf(
            PrefKeys.ABSOLUTE_TIME_VIEW,
            PrefKeys.FAB_HIDE,
            PrefKeys.SHOW_NOTIFICATIONS_FILTER
        )
    }
}

/** Errors from view model actions that the UI will need to show */
sealed class UiError(
    /** The exception associated with the error */
    open val exception: Exception,

    /** String resource with an error message to show the user */
    @StringRes val msg: Int,

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

    companion object {
        fun make(exception: Exception, action: StatusAction) =  when (action) {
            is StatusAction.Bookmark -> Bookmark(exception, action)
            is StatusAction.Favourite -> Favourite(exception, action)
            is StatusAction.Reblog -> Reblog(exception, action)
            is StatusAction.VoteInPoll -> VoteInPoll(exception, action)
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

    val uiState: StateFlow<UiState>

    val pagingDataFlow: Flow<PagingData<NotificationViewData>>

    /** Flow of changes to statusDisplayOptions, for use by the UI */
    val statusDisplayOptionsFlow: StateFlow<StatusDisplayOptions>

    /** Flow of changes to status state, for use by the UI */
    val statusSharedFlow = MutableSharedFlow<StatusUiChange>()

    /** Flow of transient errors for the UI to present */
    // Note: This is a SharedFlow instead of a StateFlow because error state does not need to be
    // retained. An error is shown once to a user and then dismissed. Re-collecting the flow
    // (e.g., after a device orientation change) should not re-show the most recent error, as it
    // will be confusing to the user.
    val errorsSharedFlow = MutableSharedFlow<UiError>()

    /** Flow of user actions received from the UI */
    private val actionSharedFlow = MutableSharedFlow<UiAction>()

    /** Accept UI actions in to actionStateFlow */
    val accept: (UiAction) -> Unit = { action ->
        viewModelScope.launch { actionSharedFlow.emit(action) }
    }

    init {
        // Handle changes to notification filters
        val notificationFilter = actionSharedFlow
            .filterIsInstance<UiAction.ApplyFilter>()
            .distinctUntilChanged()
            // Save each change back to the active account
            .onEach { action ->
                Log.d(TAG, "notificationFilter: $action")
                accountManager.activeAccount?.let { account ->
                    account.notificationsFilter = serialize(action.filter)
                    accountManager.saveAccount(account)
                }
            }
            // Load the initial filter from the active account
            .onStart {
                emit(
                    UiAction.ApplyFilter(
                        filter = deserialize(accountManager.activeAccount?.notificationsFilter)
                    )
                )
            }

        // Save the visible notification ID
        viewModelScope.launch {
            actionSharedFlow
                .filterIsInstance<UiAction.SaveVisibleId>()
                .distinctUntilChanged()
                .collectLatest { action ->
                    Log.d(TAG, "Saving visible ID: ${action.visibleId}")
                    accountManager.activeAccount?.let { account ->
                        account.lastNotificationId = action.visibleId
                        accountManager.saveAccount(account)
                    }
                }
        }

        statusDisplayOptionsFlow = MutableStateFlow(
            StatusDisplayOptions.from(
                preferences,
                accountManager.activeAccount!!
            )
        )

        // Collect changes to preferences that affect how statuses are displayed, and emit
        // updates to statusDisplayOptionsFlow.
        viewModelScope.launch {
            eventHub.events.asFlow()
                .filterIsInstance<PreferenceChangedEvent>()
                .filter { StatusDisplayOptions.prefKeys.contains(it.preferenceKey) }
                .map {
                    statusDisplayOptionsFlow.value.copy(
                        preferences,
                        it.preferenceKey,
                        accountManager.activeAccount!!
                    )
                }
                .collect {
                    statusDisplayOptionsFlow.emit(it)
                }
        }

        // Handle UiAction.ClearNotifications
        viewModelScope.launch {
            actionSharedFlow.filterIsInstance<UiAction.ClearNotifications>()
                .collectLatest {
                    repository.clearNotifications().apply {
                        if (this.isSuccessful) {
                            repository.invalidate()
                        } else {
                            errorsSharedFlow.emit(UiError.ClearNotifications(HttpException(this)))
                        }
                    }
                }
        }

        // Handle StatusAction.*
        viewModelScope.launch {
            actionSharedFlow.filterIsInstance<StatusAction>()
                .debounce(500) // avoid double-taps
                .collect { action ->
                    try {
                        when (action) {
                            is StatusAction.Bookmark ->
                                timelineCases.bookmark(
                                    action.statusViewData.actionableId,
                                    action.state
                                ).await()
                            is StatusAction.Favourite ->
                                timelineCases.favourite(
                                    action.statusViewData.actionableId,
                                    action.state
                                ).await()
                            is StatusAction.Reblog ->
                                timelineCases.reblog(
                                    action.statusViewData.actionableId,
                                    action.state
                                ).await()
                            is StatusAction.VoteInPoll ->
                                timelineCases.voteInPoll(
                                    action.statusViewData.actionableId,
                                    action.poll.id,
                                    action.choices
                                ).await()
                        }
                        statusSharedFlow.emit(StatusUiChange.from(action))
                    } catch (e: Exception) {
                        ifExpected(e) {
                            Log.d(TAG, "Failed: $action", e)
                            errorsSharedFlow.emit(UiError.make(e, action))
                        }
                    }
                }
        }

        val lastNotificationId = accountManager.activeAccount?.lastNotificationId
        Log.d(TAG, "Restoring at $lastNotificationId")

        pagingDataFlow = notificationFilter
            .flatMapLatest { action ->
                getNotifications(filters = action.filter, initialKey = lastNotificationId)
            }
            .cachedIn(viewModelScope)

        uiState = combine(notificationFilter, getUiPrefs()) { filter, prefs ->
            UiState(
                activeFilter = filter.filter,
                showAbsoluteTime = prefs.showAbsoluteTime,
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
                        isShowingContent = statusDisplayOptionsFlow.value.showSensitiveMedia ||
                            !(notification.status?.actionableStatus?.sensitive ?: false),
                        isExpanded = statusDisplayOptionsFlow.value.openSpoiler,
                        isCollapsed = true
                    )
                }
            }
    }

    /**
     * @return Flow of relevant preferences that change the UI
     */
    // TODO: Preferences should be in a repository
    private fun getUiPrefs() = eventHub.events.asFlow()
        .filterIsInstance<PreferenceChangedEvent>()
        .filter { UiPrefs.prefKeys.contains(it.preferenceKey) }
        .map { toPrefs() }
        .onStart { emit(toPrefs()) }

    private fun toPrefs() = UiPrefs(
        showAbsoluteTime = preferences.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false),
        showFabWhileScrolling = !preferences.getBoolean(PrefKeys.FAB_HIDE, false),
        showFilter = preferences.getBoolean(
            PrefKeys.SHOW_NOTIFICATIONS_FILTER,
            true
        )
    )

    companion object {
        private const val TAG = "NotificationsViewModel"
    }
}
