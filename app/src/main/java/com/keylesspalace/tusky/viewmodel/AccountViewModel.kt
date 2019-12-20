package com.keylesspalace.tusky.viewmodel

import android.util.Log
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.keylesspalace.tusky.appstore.*
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Field
import com.keylesspalace.tusky.entity.IdentityProof
import com.keylesspalace.tusky.entity.Relationship
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.*
import io.reactivex.disposables.Disposable
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import javax.inject.Inject

class AccountViewModel @Inject constructor(
        private val mastodonApi: MastodonApi,
        private val eventHub: EventHub,
        private val accountManager: AccountManager
) : ViewModel() {

    val accountData = MutableLiveData<Resource<Account>>()
    val relationshipData = MutableLiveData<Resource<Relationship>>()
    val accountFieldData = MediatorLiveData<List<Either<IdentityProof, Field>>>()

    private val identityProofData = MutableLiveData<List<IdentityProof>>()


    private val callList: MutableList<Call<*>> = mutableListOf()
    private val disposable: Disposable = eventHub.events
            .subscribe { event ->
                if (event is ProfileEditedEvent && event.newProfileData.id == accountData.value?.data?.id) {
                    accountData.postValue(Success(event.newProfileData))
                }
            }

    val isRefreshing = MutableLiveData<Boolean>().apply { value = false }
    private var isDataLoading = false

    lateinit var accountId: String
    var isSelf = false

    init {
        accountFieldData.addSource(accountData) { accountRes ->
            if(accountRes is Success) {
                accountFieldData.value = mergeAccountFieldData(identityProofData.value, accountRes.data?.fields)
            }
        }
        accountFieldData.addSource(identityProofData) { identityProofs ->
            val accountRes = accountData.value
            if(accountRes is Success) {
                accountFieldData.value = mergeAccountFieldData(identityProofs, accountRes.data?.fields)
            }
        }
    }

    private fun mergeAccountFieldData(identityProofList: List<IdentityProof>?, fieldList: List<Field>?): List<Either<IdentityProof, Field>> {
        return identityProofList.orEmpty().map { Either.Left<IdentityProof, Field>(it) }
                .plus(fieldList.orEmpty().map { Either.Right<IdentityProof, Field>(it) })
    }

    private fun obtainAccount(reload: Boolean = false) {
        if (accountData.value == null || reload) {
            isDataLoading = true
            accountData.postValue(Loading())

            val call = mastodonApi.account(accountId)
            call.enqueue(object : Callback<Account> {
                override fun onResponse(call: Call<Account>,
                                        response: Response<Account>) {
                    if (response.isSuccessful) {
                        accountData.postValue(Success(response.body()))
                    } else {
                        accountData.postValue(Error())
                    }
                    isDataLoading = false
                    isRefreshing.postValue(false)
                }

                override fun onFailure(call: Call<Account>, t: Throwable) {
                    Log.w(TAG, "failed obtaining account", t)
                    accountData.postValue(Error())
                    isDataLoading = false
                    isRefreshing.postValue(false)
                }
            })

            callList.add(call)
        }
    }

    private fun obtainRelationship(reload: Boolean = false) {
        if (relationshipData.value == null || reload) {

            relationshipData.postValue(Loading())

            val ids = listOf(accountId)
            val call = mastodonApi.relationships(ids)
            call.enqueue(object : Callback<List<Relationship>> {
                override fun onResponse(call: Call<List<Relationship>>,
                                        response: Response<List<Relationship>>) {
                    val relationships = response.body()
                    if (response.isSuccessful && relationships != null && relationships.getOrNull(0) != null) {
                        val relationship = relationships[0]
                        relationshipData.postValue(Success(relationship))
                    } else {
                        relationshipData.postValue(Error())
                    }
                }

                override fun onFailure(call: Call<List<Relationship>>, t: Throwable) {
                    Log.w(TAG, "failed obtaining relationships", t)
                    relationshipData.postValue(Error())
                }
            })

            callList.add(call)
        }
    }

    private fun obtainIdentityProof(reload: Boolean = false) {
        if (identityProofData.value == null || reload) {

            val call = mastodonApi.identityProofs(accountId)
            call.enqueue(object : Callback<List<IdentityProof>> {
                override fun onResponse(call: Call<List<IdentityProof>>,
                                        response: Response<List<IdentityProof>>) {
                    val proofs = response.body()
                    if (response.isSuccessful && proofs != null ) {
                        identityProofData.postValue(proofs)
                    } else {
                        identityProofData.postValue(emptyList())
                    }
                }

                override fun onFailure(call: Call<List<IdentityProof>>, t: Throwable) {
                    Log.w(TAG, "failed obtaining identity proofs", t)
                }
            })

            callList.add(call)
        }
    }

    fun changeFollowState() {
        val relationship = relationshipData.value?.data
        if (relationship?.following == true || relationship?.requested == true) {
            changeRelationship(RelationShipAction.UNFOLLOW)
        } else {
            changeRelationship(RelationShipAction.FOLLOW)
        }
    }

    fun changeBlockState() {
        if (relationshipData.value?.data?.blocking == true) {
            changeRelationship(RelationShipAction.UNBLOCK)
        } else {
            changeRelationship(RelationShipAction.BLOCK)
        }
    }

    fun changeMuteState() {
        if (relationshipData.value?.data?.muting == true) {
            changeRelationship(RelationShipAction.UNMUTE)
        } else {
            changeRelationship(RelationShipAction.MUTE)
        }
    }

    fun muteDomain(instance: String) {
        mastodonApi.blockDomain(instance).enqueue(object: Callback<Any> {
            override fun onResponse(call: Call<Any>, response: Response<Any>) {
                if (response.isSuccessful) {
                    eventHub.dispatch(DomainMuteEvent(instance))
                } else {
                    Log.e(TAG, String.format("Error muting %s", instance))
                }
            }

            override fun onFailure(call: Call<Any>, t: Throwable) {
                Log.e(TAG, String.format("Error muting %s", instance), t)
            }
        })
    }

    fun changeShowReblogsState() {
        if (relationshipData.value?.data?.showingReblogs == true) {
            changeRelationship(RelationShipAction.FOLLOW, false)
        } else {
            changeRelationship(RelationShipAction.FOLLOW, true)
        }
    }

    private fun changeRelationship(relationshipAction: RelationShipAction, showReblogs: Boolean = true) {
        val relation = relationshipData.value?.data
        val account = accountData.value?.data

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
            }
            relationshipData.postValue(Loading(newRelation))
        }

        val callback = object : Callback<Relationship> {
            override fun onResponse(call: Call<Relationship>,
                                    response: Response<Relationship>) {
                val relationship = response.body()
                if (response.isSuccessful && relationship != null) {
                    relationshipData.postValue(Success(relationship))

                    when (relationshipAction) {
                        RelationShipAction.UNFOLLOW -> eventHub.dispatch(UnfollowEvent(accountId))
                        RelationShipAction.BLOCK -> eventHub.dispatch(BlockEvent(accountId))
                        RelationShipAction.MUTE -> eventHub.dispatch(MuteEvent(accountId))
                        else -> {
                        }
                    }

                } else {
                    relationshipData.postValue(Error(relation))
                }

            }

            override fun onFailure(call: Call<Relationship>, t: Throwable) {
                relationshipData.postValue(Error(relation))
            }
        }

        val call = when (relationshipAction) {
            RelationShipAction.FOLLOW -> mastodonApi.followAccount(accountId, showReblogs)
            RelationShipAction.UNFOLLOW -> mastodonApi.unfollowAccount(accountId)
            RelationShipAction.BLOCK -> mastodonApi.blockAccount(accountId)
            RelationShipAction.UNBLOCK -> mastodonApi.unblockAccount(accountId)
            RelationShipAction.MUTE -> mastodonApi.muteAccount(accountId)
            RelationShipAction.UNMUTE -> mastodonApi.unmuteAccount(accountId)
        }

        call.enqueue(callback)
        callList.add(call)

    }

    override fun onCleared() {
        callList.forEach {
            it.cancel()
        }
        disposable.dispose()
    }

    fun refresh() {
        reload(true)
    }

    private fun reload(isReload: Boolean = false) {
        if (isDataLoading)
            return
        accountId.let {
            obtainAccount(isReload)
            obtainIdentityProof()
            if (!isSelf)
                obtainRelationship(isReload)
        }

    }

    fun setAccountInfo(accountId: String) {
        this.accountId = accountId
        this.isSelf = accountManager.activeAccount?.accountId == accountId
        reload(false)
    }

    enum class RelationShipAction {
        FOLLOW, UNFOLLOW, BLOCK, UNBLOCK, MUTE, UNMUTE
    }

    companion object {
        const val TAG = "AccountViewModel"
    }
}