package com.keylesspalace.tusky.components.notifications

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PollVoteEvent
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.components.timeline.util.ifExpected
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.usecase.TimelineCases
import com.keylesspalace.tusky.util.CardViewMode
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.deserialize
import com.keylesspalace.tusky.util.serialize
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.viewdata.NotificationViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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
import javax.inject.Inject

data class UiState(
    /** Filtered notification types */
    val activeFilter: Set<Notification.Type> = emptySet(),

    /** True if statuses should display absolute time */
    val showAbsoluteTime: Boolean = false,

    /** True if the UI to filter notifications should be shown */
    val showFilterOptions: Boolean = false,

    /** True if the FAB should be shown */
    val showFab: Boolean = true,

    /** True if media previews should be shown */
    val showMediaPreview: Boolean = false
)

// TODO: The status functions this exposes (reblog, favourite, bookmark, etc) are very similar
// to those in TimelineViewModel. Investigate how to de-duplicate them where practical.

// TODO: Maybe view models should implement StatusActionListener? If the interface's methods
// included the status data as well as the position there's no need to go through the hosting
// fragment or activity to get the data. That would simplify the code slightly and save a function
// call.

// TODO: When notifications are fetched, save the ID of the newest one.

/** Actions the user can trigger from the UI */
sealed class UiAction {
    /** User wants to apply a new filter to the notification list */
    data class ApplyFilter(val filter: Set<Notification.Type>) : UiAction()

    /** User is leaving the fragment, save the ID of the visible notification */
    data class SaveVisibleId(val visibleId: String) : UiAction()
}

/** Preferences this view reacts to */
data class Prefs(
    val showAbsoluteTime: Boolean,
    val showFab: Boolean,
    val showFilter: Boolean,
    val showMediaPreviews: Boolean
) {
    companion object {
        /** Relevant preference keys */
        val prefKeys = setOf(
            PrefKeys.ABSOLUTE_TIME_VIEW,
            PrefKeys.FAB_HIDE,
            PrefKeys.SHOW_NOTIFICATIONS_FILTER,
            PrefKeys.MEDIA_PREVIEW_ENABLED,
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationsRepository,
    private val preferences: SharedPreferences,
    private val accountManager: AccountManager,
    private val timelineCases: TimelineCases,
    private val eventHub: EventHub
) : ViewModel() {

    val uiState: StateFlow<UiState>

    val pagingDataFlow: Flow<PagingData<NotificationViewData.Concrete>>

    val statusDisplayOptions: StatusDisplayOptions

    private val actionStateFlow = MutableSharedFlow<UiAction>()

    /** Accept UI actions and convert them to new state */
    val accept: (UiAction) -> Unit = { action ->
        viewModelScope.launch { actionStateFlow.emit(action) }
    }

    init {
        statusDisplayOptions = StatusDisplayOptions(
            animateAvatars = preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false),
            mediaPreviewEnabled = accountManager.activeAccount!!.mediaPreviewEnabled,
            useAbsoluteTime = preferences.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false),
            showBotOverlay = preferences.getBoolean(PrefKeys.SHOW_BOT_OVERLAY, true),
            useBlurhash = preferences.getBoolean(PrefKeys.USE_BLURHASH, true),
            cardViewMode = CardViewMode.NONE,
            confirmReblogs = preferences.getBoolean(PrefKeys.CONFIRM_REBLOGS, true),
            confirmFavourites = preferences.getBoolean(PrefKeys.CONFIRM_FAVOURITES, false),
            hideStats = preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false),
            animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false),
            showSensitiveMedia = accountManager.activeAccount!!.alwaysShowSensitiveMedia,
            openSpoiler = accountManager.activeAccount!!.alwaysOpenSpoiler
        )

        // Process changes to notification filters
        val notificationFilter = actionStateFlow
            .filterIsInstance<UiAction.ApplyFilter>()
            .distinctUntilChanged()
            // Save each change back to the active account
            .onEach { action ->
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

        // Save the visible notification Id
        viewModelScope.launch {
            actionStateFlow
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

        val preferencesStateFlow = getPreferences()

        val lastNotificationId = accountManager.activeAccount?.lastNotificationId
        Log.d(TAG, "Restoring at $lastNotificationId")

        pagingDataFlow = notificationFilter
            .flatMapLatest { action ->
                getNotifications(filters = action.filter, initialKey = lastNotificationId)
            }
            .cachedIn(viewModelScope)

        uiState = combine(notificationFilter, preferencesStateFlow) {
                filter, prefs ->
            UiState(
                activeFilter = filter.filter,
                showAbsoluteTime = prefs.showAbsoluteTime,
                showFilterOptions = prefs.showFilter,
                showFab = prefs.showFab,
                showMediaPreview = prefs.showMediaPreviews
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
    ): Flow<PagingData<NotificationViewData.Concrete>> {
        return repository.getNotificationsStream(filter = filters, initialKey = initialKey)
            .map { pagingData ->
                pagingData.map { notification ->
                    notification.toViewData(
                        isShowingContent = statusDisplayOptions.showSensitiveMedia ||
                            !(notification.status?.actionableStatus?.sensitive ?: false),
                        isExpanded = statusDisplayOptions.openSpoiler,
                        isCollapsed = true
                    )
                }
            }
    }

    /**
     * @return Flow of relevant preferences for this view over time
     */
    // TODO: Preferences should be in a repository
    private fun getPreferences() = eventHub.events.asFlow()
        .filterIsInstance<PreferenceChangedEvent>()
        .filter { Prefs.prefKeys.contains(it.preferenceKey) }
        .distinctUntilChanged()
        .map { toPrefs() }
        .onStart { emit(toPrefs()) }

    private fun toPrefs() = Prefs(
        showAbsoluteTime = preferences.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false),
        showFab = !preferences.getBoolean(PrefKeys.FAB_HIDE, false),
        // TODO: If this has changed then the adapter needs to do a full refresh
        showMediaPreviews = accountManager.activeAccount!!.mediaPreviewEnabled,
        showFilter = preferences.getBoolean(
            PrefKeys.SHOW_NOTIFICATIONS_FILTER,
            true
        )
    )

    // TODO: Listen for eventhub events here, and update the UI model, instead of the fragment
    // listening for events.

    // TODO: Copied from TimelineViewModel
    fun reblog(
        reblog: Boolean,
        statusViewData: StatusViewData.Concrete
    ): Job = viewModelScope.launch {
        try {
            timelineCases.reblog(statusViewData.actionableId, reblog).await()
        } catch (t: Exception) {
            ifExpected(t) {
                Log.d(TAG, "Failed to reblog status " + statusViewData.actionableId, t)
            }
        }
    }

    // TODO: Copied from TimelineViewModel
    fun favorite(
        favorite: Boolean,
        statusViewData: StatusViewData.Concrete
    ): Job = viewModelScope.launch {
        try {
            timelineCases.favourite(statusViewData.actionableId, favorite).await()
        } catch (t: Exception) {
            ifExpected(t) {
                Log.d(TAG, "Failed to favourite status " + statusViewData.actionableId, t)
            }
        }
    }

    // TODO: Copied from TimelineViewModel
    fun bookmark(
        bookmark: Boolean,
        statusViewData: StatusViewData.Concrete
    ): Job = viewModelScope.launch {
        try {
            timelineCases.bookmark(statusViewData.actionableId, bookmark).await()
        } catch (t: Exception) {
            ifExpected(t) {
                Log.d(TAG, "Failed to bookmark status " + statusViewData.actionableId, t)
            }
        }
    }

    // TODO: Copied from TimelineViewModel
    fun voteInPoll(choices: List<Int>, statusViewData: StatusViewData.Concrete): Job = viewModelScope.launch {
        val poll = statusViewData.status.actionableStatus.poll ?: run {
            Log.w(TAG, "No poll on status ${statusViewData.id}")
            return@launch
        }

        // TODO: This is the same functionality as the code in TimelineViewModel; the user is
        // shown their voting choice as successful before the API call returns. But this is
        // inconsistent with favourite, bookmark, etc. There the UI is only updated after a
        // successful call.
        //
        // Suspect that button debouncing, or a third state (waiting-on-network) is needed here.
        // Something like: If the user clicks to vote, or bookmark, etc, the request is sent and
        // a coroutine starts and sleeps N ms. Then it changes the button to a progress spinner.
        // When the request completes it cancels the coroutine. In the common case the button
        // still changes effectively immediately, and in the uncommon case the user gets feedback
        // that it failed.
        //
        // Also, if it failed, maybe show a badge (red exclamation mark?) on the button to
        // make it clear that something went wrong.
        val votedPoll = poll.votedCopy(choices)
        eventHub.dispatch(PollVoteEvent(statusViewData.id, votedPoll))

        try {
            timelineCases.voteInPoll(statusViewData.actionableId, poll.id, choices).await()
        } catch (t: Exception) {
            ifExpected(t) {
                Log.d(TAG, "Failed to vote in poll: " + statusViewData.actionableId, t)
            }
        }
    }

    companion object {
        private const val TAG = "NotificationsViewModel"
    }
}
