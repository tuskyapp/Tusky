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


    fun obtainAccount(accountId: String, reload: Boolean = false) {
        if(accountData.value == null || reload) {

            accountData.postValue(Loading())

            mastodonApi.account(accountId).enqueue(object : Callback<Account> {
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
        }
    }

    fun obtainRelationship(accountId: String, reload: Boolean = false) {
        if(relationshipData.value == null || reload) {

            relationshipData.postValue(Loading())

            val ids = listOf(accountId)
            mastodonApi.relationships(ids).enqueue(object : Callback<List<Relationship>> {
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
        }
    }

    fun follow(id: String) {
        changeRelationship(RelationShipAction.FOLLOW, id)
    }

    fun unfollow(id: String) {
        changeRelationship(RelationShipAction.UNFOLLOW, id)
    }


    fun block(id: String) {
        changeRelationship(RelationShipAction.BLOCK, id)
    }

    fun unblock(id: String) {
        changeRelationship(RelationShipAction.UNBLOCK, id)
    }

    fun mute(id: String) {
        changeRelationship(RelationShipAction.MUTE, id)
    }

    fun unmute(id: String) {
        changeRelationship(RelationShipAction.UNMUTE, id)
    }

    fun showReblogs(id: String) {
        changeRelationship(RelationShipAction.FOLLOW, id, true)
    }

    fun hideReblogs(id: String) {
        changeRelationship(RelationShipAction.FOLLOW, id, false)
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

        when(relationshipAction) {
            RelationShipAction.FOLLOW ->  mastodonApi.followAccount(id, showReblogs).enqueue(callback)
            RelationShipAction.UNFOLLOW ->  mastodonApi.unfollowAccount(id).enqueue(callback)
            RelationShipAction.BLOCK ->  mastodonApi.blockAccount(id).enqueue(callback)
            RelationShipAction.UNBLOCK ->  mastodonApi.unblockAccount(id).enqueue(callback)
            RelationShipAction.MUTE ->  mastodonApi.muteAccount(id).enqueue(callback)
            RelationShipAction.UNMUTE ->  mastodonApi.unmuteAccount(id).enqueue(callback)
        }

    }

    override fun onCleared() {

        
    }

    enum class RelationShipAction {
        FOLLOW, UNFOLLOW, BLOCK, UNBLOCK, MUTE, UNMUTE
    }

}