package com.keylesspalace.tusky.components.domainblocks

import android.view.View
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.onFailure
import com.keylesspalace.tusky.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DomainBlocksViewModel @Inject constructor(
    private val repo: DomainBlocksRepository
) : ViewModel() {

    val domainPager = repo.domainPager.cachedIn(viewModelScope)

    private val _uiEvents = MutableSharedFlow<SnackbarEvent>()
    val uiEvents: SharedFlow<SnackbarEvent> = _uiEvents.asSharedFlow()

    fun block(domain: String) {
        viewModelScope.launch {
            repo.block(domain).onFailure { e ->
                _uiEvents.emit(
                    SnackbarEvent(
                        message = R.string.error_blocking_domain,
                        domain = domain,
                        throwable = e,
                        actionText = R.string.action_retry,
                        action = { block(domain) }
                    )
                )
            }
        }
    }

    fun unblock(domain: String) {
        viewModelScope.launch {
            repo.unblock(domain).fold({
                _uiEvents.emit(
                    SnackbarEvent(
                        message = R.string.confirmation_domain_unmuted,
                        domain = domain,
                        throwable = null,
                        actionText = R.string.action_undo,
                        action = { block(domain) }
                    )
                )
            }, { e ->
                _uiEvents.emit(
                    SnackbarEvent(
                        message = R.string.error_unblocking_domain,
                        domain = domain,
                        throwable = e,
                        actionText = R.string.action_retry,
                        action = { unblock(domain) }
                    )
                )
            })
        }
    }
}

class SnackbarEvent(
    @StringRes val message: Int,
    val domain: String,
    val throwable: Throwable?,
    @StringRes val actionText: Int,
    val action: (View) -> Unit
)
