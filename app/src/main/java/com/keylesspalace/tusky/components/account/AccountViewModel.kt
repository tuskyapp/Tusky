package com.keylesspalace.tusky.components.account

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.appstore.BlockEvent
import com.keylesspalace.tusky.appstore.DomainMuteEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.MuteEvent
import com.keylesspalace.tusky.appstore.ProfileEditedEvent
import com.keylesspalace.tusky.appstore.UnfollowEvent
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Relationship
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.Error
import com.keylesspalace.tusky.util.Loading
import com.keylesspalace.tusky.util.Resource
import com.keylesspalace.tusky.util.Success
import com.keylesspalace.tusky.util.getDomain
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AccountViewModel @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val eventHub: EventHub,
    accountManager: AccountManager
) : ViewModel() {

    private val _accountData = MutableStateFlow(null as Resource<Account>?)
    val accountData: StateFlow<Resource<Account>?> = _accountData.asStateFlow()

    private val _relationshipData = MutableStateFlow(null as Resource<Relationship>?)
    val relationshipData: StateFlow<Resource<Relationship>?> = _relationshipData.asStateFlow()

    private val _noteSaved = MutableStateFlow(false)
    val noteSaved: StateFlow<Boolean> = _noteSaved.asStateFlow()

    private val _refreshState = MutableStateFlow(RefreshState.INITIAL)
    val refreshState: StateFlow<RefreshState> = _refreshState.asStateFlow()

    lateinit var accountId: String
    var isSelf = false

    /** the domain of the viewed account **/
    var domain = ""

    /** True if the viewed account has the same domain as the active account */
    var isFromOwnDomain = false

    private var noteUpdateJob: Job? = null

    private val activeAccount = accountManager.activeAccount!!

    init {
        viewModelScope.launch {
            eventHub.events.collect { event ->
                if (event is ProfileEditedEvent && event.newProfileData.id == _accountData.value?.data?.id) {
                    _accountData.value = Success(event.newProfileData)
                }
            }
        }
    }

    private fun obtainAccount(reload: Boolean = false) {
        if (_accountData.value == null || reload) {
            _refreshState.value = RefreshState.REFRESHING
            _accountData.value = Loading()

            viewModelScope.launch {
                mastodonApi.account(accountId)
                    .fold(
                        { account ->
                            domain = getDomain(account.url)
                            isFromOwnDomain = domain == activeAccount.domain

                            _accountData.value = Success(account)
                            _refreshState.value = RefreshState.IDLE
                        },
                        { t ->
                            Log.w(TAG, "failed obtaining account", t)
                            _accountData.value = Error(cause = t)
                            _refreshState.value = RefreshState.IDLE
                        }
                    )
            }
        }
    }

    private fun obtainRelationship(reload: Boolean = false) {
        if (_relationshipData.value == null || reload) {
            _relationshipData.value = Loading()

            viewModelScope.launch {
                mastodonApi.relationships(listOf(accountId))
                    .fold(
                        { relationships ->
                            _relationshipData.value =
                                if (relationships.isNotEmpty()) {
                                    Success(
                                        relationships[0]
                                    )
                                } else {
                                    Error()
                                }
                        },
                        { t ->
                            Log.w(TAG, "failed obtaining relationships", t)
                            _relationshipData.value = Error(cause = t)
                        }
                    )
            }
        }
    }

    fun changeFollowState() {
        val relationship = _relationshipData.value?.data
        if (relationship?.following == true || relationship?.requested == true) {
            changeRelationship(RelationShipAction.UNFOLLOW)
        } else {
            changeRelationship(RelationShipAction.FOLLOW)
        }
    }

    fun changeBlockState() {
        if (_relationshipData.value?.data?.blocking == true) {
            changeRelationship(RelationShipAction.UNBLOCK)
        } else {
            changeRelationship(RelationShipAction.BLOCK)
        }
    }

    fun muteAccount(notifications: Boolean, duration: Int?) {
        changeRelationship(RelationShipAction.MUTE, notifications, duration)
    }

    fun unmuteAccount() {
        changeRelationship(RelationShipAction.UNMUTE)
    }

    fun changeSubscribingState() {
        val relationship = _relationshipData.value?.data
        if (relationship?.notifying == true || // Mastodon 3.3.0rc1
            relationship?.subscribing == true // Pleroma
        ) {
            changeRelationship(RelationShipAction.UNSUBSCRIBE)
        } else {
            changeRelationship(RelationShipAction.SUBSCRIBE)
        }
    }

    fun blockDomain(instance: String) {
        viewModelScope.launch {
            mastodonApi.blockDomain(instance).fold({
                eventHub.dispatch(DomainMuteEvent(instance))
                val relation = _relationshipData.value?.data
                if (relation != null) {
                    _relationshipData.value = Success(relation.copy(blockingDomain = true))
                }
            }, { e ->
                Log.e(TAG, "Error muting $instance", e)
            })
        }
    }

    fun unblockDomain(instance: String) {
        viewModelScope.launch {
            mastodonApi.unblockDomain(instance).fold({
                val relation = _relationshipData.value?.data
                if (relation != null) {
                    _relationshipData.value = Success(relation.copy(blockingDomain = false))
                }
            }, { e ->
                Log.e(TAG, "Error unmuting $instance", e)
            })
        }
    }

    fun changeShowReblogsState() {
        if (_relationshipData.value?.data?.showingReblogs == true) {
            changeRelationship(RelationShipAction.FOLLOW, false)
        } else {
            changeRelationship(RelationShipAction.FOLLOW, true)
        }
    }

    /**
     * @param parameter showReblogs if RelationShipAction.FOLLOW, notifications if MUTE
     */
    private fun changeRelationship(
        relationshipAction: RelationShipAction,
        parameter: Boolean? = null,
        duration: Int? = null
    ) = viewModelScope.launch {
        val relation = _relationshipData.value?.data
        val account = _accountData.value?.data
        val isMastodon = _relationshipData.value?.data?.notifying != null

        if (relation != null && account != null) {
            // optimistically post new state for faster response

            val newRelation = when (relationshipAction) {
                RelationShipAction.FOLLOW -> {
                    if (account.locked) {
                        relation.copy(requested = true)
                    } else {
                        relation.copy(following = true)
                    }
                }
                RelationShipAction.UNFOLLOW -> relation.copy(following = false)
                RelationShipAction.BLOCK -> relation.copy(blocking = true)
                RelationShipAction.UNBLOCK -> relation.copy(blocking = false)
                RelationShipAction.MUTE -> relation.copy(muting = true)
                RelationShipAction.UNMUTE -> relation.copy(muting = false)
                RelationShipAction.SUBSCRIBE -> {
                    if (isMastodon) {
                        relation.copy(notifying = true)
                    } else {
                        relation.copy(subscribing = true)
                    }
                }
                RelationShipAction.UNSUBSCRIBE -> {
                    if (isMastodon) {
                        relation.copy(notifying = false)
                    } else {
                        relation.copy(subscribing = false)
                    }
                }
            }
            _relationshipData.value = Loading(newRelation)
        }

        val relationshipCall = when (relationshipAction) {
            RelationShipAction.FOLLOW -> mastodonApi.followAccount(
                accountId,
                showReblogs = parameter ?: true
            )
            RelationShipAction.UNFOLLOW -> mastodonApi.unfollowAccount(accountId)
            RelationShipAction.BLOCK -> mastodonApi.blockAccount(accountId)
            RelationShipAction.UNBLOCK -> mastodonApi.unblockAccount(accountId)
            RelationShipAction.MUTE -> mastodonApi.muteAccount(
                accountId,
                parameter ?: true,
                duration
            )
            RelationShipAction.UNMUTE -> mastodonApi.unmuteAccount(accountId)
            RelationShipAction.SUBSCRIBE -> {
                if (isMastodon) {
                    mastodonApi.followAccount(accountId, notify = true)
                } else {
                    mastodonApi.subscribeAccount(accountId)
                }
            }
            RelationShipAction.UNSUBSCRIBE -> {
                if (isMastodon) {
                    mastodonApi.followAccount(accountId, notify = false)
                } else {
                    mastodonApi.unsubscribeAccount(accountId)
                }
            }
        }

        relationshipCall.fold(
            { relationship ->
                _relationshipData.value = Success(relationship)

                when (relationshipAction) {
                    RelationShipAction.UNFOLLOW -> eventHub.dispatch(UnfollowEvent(accountId))
                    RelationShipAction.BLOCK -> eventHub.dispatch(BlockEvent(accountId))
                    RelationShipAction.MUTE -> eventHub.dispatch(MuteEvent(accountId))
                    else -> { }
                }
            },
            { t ->
                Log.w(TAG, "failed loading relationship", t)
                _relationshipData.value = Error(relation, cause = t)
            }
        )
    }

    fun noteChanged(newNote: String) {
        _noteSaved.value = false
        noteUpdateJob?.cancel()
        noteUpdateJob = viewModelScope.launch {
            delay(1500)
            mastodonApi.updateAccountNote(accountId, newNote)
                .fold(
                    {
                        _noteSaved.value = true
                        delay(4000)
                        _noteSaved.value = false
                    },
                    { t ->
                        Log.w(TAG, "Error updating note", t)
                    }
                )
        }
    }

    fun refresh() {
        reload(true)
    }

    private fun reload(isReload: Boolean = false) {
        if (_refreshState.value == RefreshState.REFRESHING) {
            return
        }
        accountId.let {
            obtainAccount(isReload)
            if (!isSelf) {
                obtainRelationship(isReload)
            }
        }
    }

    fun setAccountInfo(accountId: String) {
        this.accountId = accountId
        this.isSelf = activeAccount.accountId == accountId
        reload(false)
    }

    enum class RelationShipAction {
        FOLLOW,
        UNFOLLOW,
        BLOCK,
        UNBLOCK,
        MUTE,
        UNMUTE,
        SUBSCRIBE,
        UNSUBSCRIBE
    }

    enum class RefreshState {
        INITIAL,
        REFRESHING,
        IDLE
    }

    companion object {
        const val TAG = "AccountViewModel"
    }
}
