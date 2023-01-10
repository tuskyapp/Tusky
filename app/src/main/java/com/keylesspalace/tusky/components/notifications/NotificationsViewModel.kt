package com.keylesspalace.tusky.components.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.keylesspalace.tusky.network.MastodonApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class NotificationsUiState(
    // Dummy, just to have something to represent in the state
    val foo: Int
)

class NotificationsViewModel @Inject constructor(
    private val mastodonApi: MastodonApi
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState(foo = 1))
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    val flow = Pager(PagingConfig(pageSize = 30)) {
        NotificationsPagingSource(mastodonApi)
    }.flow.cachedIn(viewModelScope)
}
