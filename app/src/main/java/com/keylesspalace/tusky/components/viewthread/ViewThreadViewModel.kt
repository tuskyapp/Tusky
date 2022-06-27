package com.keylesspalace.tusky.components.viewthread

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.getOrElse
import autodispose2.AutoDispose
import autodispose2.androidx.lifecycle.AndroidLifecycleScopeProvider
import com.keylesspalace.tusky.appstore.BlockEvent
import com.keylesspalace.tusky.appstore.BookmarkEvent
import com.keylesspalace.tusky.appstore.Event
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.FavoriteEvent
import com.keylesspalace.tusky.appstore.PinEvent
import com.keylesspalace.tusky.appstore.ReblogEvent
import com.keylesspalace.tusky.appstore.StatusComposedEvent
import com.keylesspalace.tusky.appstore.StatusDeletedEvent
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineViewModel
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.keylesspalace.tusky.viewmodel.ListsViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.rx3.await
import javax.inject.Inject

class ViewThreadViewModel @Inject constructor(
    private val api: MastodonApi,
    private val eventHub: EventHub,
    private val filterModel: FilterModel,
    accountManager: AccountManager
): ViewModel() {

    private val _uiState: MutableStateFlow<ThreadUiState> = MutableStateFlow(ThreadUiState.Loading)
    val uiState: Flow<ThreadUiState>
        get() = _uiState

    private val _errors = MutableSharedFlow<Throwable>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val errors: Flow<Throwable>
        get() = _errors

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
                    if (event is FavoriteEvent) {
                        handleFavEvent(event)
                    } else if (event is ReblogEvent) {
                        handleReblogEvent(event)
                    } else if (event is BookmarkEvent) {
                        handleBookmarkEvent(event)
                    } else if (event is PinEvent) {
                        handlePinEvent(event)
                    } else if (event is BlockEvent) {
                        removeAllByAccountId(event.accountId)
                    } else if (event is StatusComposedEvent) {
                        handleStatusComposedEvent(event)
                    } else if (event is StatusDeletedEvent) {
                        handleStatusDeletedEvent(event)
                    }
                }
        }

        loadFilters()
    }

    private fun handleFavEvent(event: FavoriteEvent) {
    }

    private fun handleReblogEvent(event: ReblogEvent) {
    }

    private fun handleBookmarkEvent(event: BookmarkEvent) {
    }

    private fun handlePinEvent(event: PinEvent) {
    }

    private fun removeAllByAccountId(accountId: String) {
    }

    private fun handleStatusComposedEvent(event: StatusComposedEvent) {
    }

    private fun handleStatusDeletedEvent(event: StatusDeletedEvent) {
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

                val ancestors = statusContext.ancestors.map { status -> status.toViewData()}
                val detailedStatus = status.toViewData(true)
                val descendants = statusContext.descendants.map { status -> status.toViewData()}

                _uiState.value = ThreadUiState.Success(ancestors + detailedStatus + descendants, RevealButtonState.REVEAL)

            },{ throwable ->
                Log.w("ViewThreadViewModel", "failed to load status context", throwable)

                _uiState.value = ThreadUiState.Success(
                    listOf(status.toViewData(true)),
                    RevealButtonState.HIDDEN
                )
            })
        }
    }

    fun toggleRevealButton() {
        _uiState.update { uiState ->
            if (uiState is ThreadUiState.Success && uiState.revealButton != RevealButtonState.HIDDEN) {
                uiState.copy(
                    revealButton = if (uiState.revealButton == RevealButtonState.HIDE) {
                        RevealButtonState.REVEAL
                    } else {
                        RevealButtonState.HIDE
                    }
                )
            } else {
                uiState
            }
        }
    }

    private fun handleEvent(event: Event) {
       
    }

    private fun loadFilters() {
        viewModelScope.launch {
            val filters = try {
                api.getFilters().await()
            } catch (t: Exception) {
                Log.w(TAG, "Failed to fetch filters", t)
                return@launch
            }
            filterModel.initWithFilters(
                filters.filter { filter ->
                    filter.context.contains(Filter.THREAD)
                }
            )


        }
    }

    private fun Status.toViewData(detailed: Boolean = false): StatusViewData.Concrete {
        return toViewData(
            isShowingContent = alwaysShowSensitiveMedia || !actionableStatus.sensitive,
            isExpanded = alwaysOpenSpoiler,
            isCollapsed = !detailed,
            isDetailed = detailed
        )
    }

    companion object {
        private const val TAG = "ViewThreadViewModel"
    }
}

sealed interface ThreadUiState {
    object Loading: ThreadUiState
    class Error(val throwable: Throwable): ThreadUiState
    data class Success(
        val statuses: List<StatusViewData.Concrete>,
        val revealButton: RevealButtonState
    ): ThreadUiState
}

enum class RevealButtonState {
    HIDDEN, REVEAL, HIDE
}
