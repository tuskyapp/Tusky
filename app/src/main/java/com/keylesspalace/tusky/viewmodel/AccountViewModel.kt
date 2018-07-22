package com.keylesspalace.tusky.viewmodel

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.keylesspalace.tusky.appstore.BlockEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.MuteEvent
import com.keylesspalace.tusky.appstore.UnfollowEvent
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Relationship
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.Error
import com.keylesspalace.tusky.util.Loading
import com.keylesspalace.tusky.util.Resource
import com.keylesspalace.tusky.util.Success
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import javax.inject.Inject

class AccountViewModel  @Inject constructor(
            private val mastodonApi: MastodonApi,
            private val eventHub: EventHub
    ): ViewModel() {

    val accountData = MutableLiveData<Resource<Account>>()
    val relationshipData = MutableLiveData<Resource<Relationship>>()

    private val callList: MutableList<Call<*>> = mutableListOf()


    fun obtainAccount(accountId: String, reload: Boolean = false) {
        if(accountData.value == null || reload) {

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
                }

                override fun onFailure(call: Call<Account>, t: Throwable) {
                    accountData.postValue(Error())
                }
            })

            callList.add(call)
        }
    }

    fun obtainRelationship(accountId: String, reload: Boolean = false) {
        if(relationshipData.value == null || reload) {

            relationshipData.postValue(Loading())

            val ids = listOf(accountId)
            val call = mastodonApi.relationships(ids)
                    call.enqueue(object : Callback<List<Relationship>> {
                override fun onResponse(call: Call<List<Relationship>>,
                                        response: Response<List<Relationship>>) {
                    val relationships = response.body()
                    if (response.isSuccessful && relationships != null) {
                        val relationship = relationships[0]
                        relationshipData.postValue(Success(relationship))
                    } else {
                        relationshipData.postValue(Error())
                    }
                }

                override fun onFailure(call: Call<List<Relationship>>, t: Throwable) {
                    relationshipData.postValue(Error())
                }
            })

            callList.add(call)
        }
    }

    fun changeFollowState(id: String) {
        val relationship = relationshipData.value?.data
        if (relationship?.following == true || relationship?.requested == true) {
            changeRelationship(RelationShipAction.UNFOLLOW, id)
        } else {
            changeRelationship(RelationShipAction.FOLLOW, id)
        }
    }

    fun changeBlockState(id: String) {
        if (relationshipData.value?.data?.blocking == true) {
            changeRelationship(RelationShipAction.UNBLOCK, id)
        } else {
            changeRelationship(RelationShipAction.BLOCK, id)
        }
    }

    fun changeMuteState(id: String) {
        if (relationshipData.value?.data?.muting == true) {
            changeRelationship(RelationShipAction.UNMUTE, id)
        } else {
            changeRelationship(RelationShipAction.MUTE, id)
        }
    }

    fun changeShowReblogsState(id: String) {
        if (relationshipData.value?.data?.showingReblogs == true) {
            changeRelationship(RelationShipAction.FOLLOW, id, false)
        } else {
            changeRelationship(RelationShipAction.FOLLOW, id, true)
        }
    }

    private fun changeRelationship(relationshipAction: RelationShipAction, id: String, showReblogs: Boolean = true) {
        val relation = relationshipData.value?.data
        val account = accountData.value?.data

        if(relation != null && account != null) {
            // optimistically post new state for faster response

            val newRelation = when(relationshipAction) {
                RelationShipAction.FOLLOW -> {
                    if (account.locked) {
                        relation.copy(requested = true)
                    } else {
                        relation.copy(following = true)
                    }
                }
                RelationShipAction.UNFOLLOW ->  relation.copy(following = false)
                RelationShipAction.BLOCK ->  relation.copy(blocking = true)
                RelationShipAction.UNBLOCK ->  relation.copy(blocking = false)
                RelationShipAction.MUTE ->  relation.copy(muting = true)
                RelationShipAction.UNMUTE ->  relation.copy(muting = false)
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
                        RelationShipAction.UNFOLLOW -> eventHub.dispatch(UnfollowEvent(id))
                        RelationShipAction.BLOCK -> eventHub.dispatch(BlockEvent(id))
                        RelationShipAction.MUTE -> eventHub.dispatch(MuteEvent(id))
                        else -> {}
                    }

                } else {
                    relationshipData.postValue(Error(relation))
                }

            }

            override fun onFailure(call: Call<Relationship>, t: Throwable) {
                relationshipData.postValue(Error(relation))
            }
        }

        val call = when(relationshipAction) {
            RelationShipAction.FOLLOW ->  mastodonApi.followAccount(id, showReblogs)
            RelationShipAction.UNFOLLOW ->  mastodonApi.unfollowAccount(id)
            RelationShipAction.BLOCK ->  mastodonApi.blockAccount(id)
            RelationShipAction.UNBLOCK ->  mastodonApi.unblockAccount(id)
            RelationShipAction.MUTE ->  mastodonApi.muteAccount(id)
            RelationShipAction.UNMUTE ->  mastodonApi.unmuteAccount(id)
        }

        call.enqueue(callback)
        callList.add(call)

    }

    override fun onCleared() {
        callList.forEach {
            it.cancel()
        }
    }

    enum class RelationShipAction {
        FOLLOW, UNFOLLOW, BLOCK, UNBLOCK, MUTE, UNMUTE
    }

}