package com.keylesspalace.tusky.components.conversation

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.Transformations.map
import androidx.lifecycle.ViewModel
import javax.inject.Inject

class ConversationsViewModel  @Inject constructor(
            private val repository: ConversationsRepository
    ): ViewModel() {

    private val accountId = MutableLiveData<Long>()
    private val repoResult = map(accountId) {
        repository.conversations(it, 30)
    }

    val conversations = Transformations.switchMap(repoResult) { it.pagedList }
    val networkState = Transformations.switchMap(repoResult) { it.networkState }
    val refreshState = Transformations.switchMap(repoResult) { it.refreshState }

    fun refresh() {
        repoResult.value?.refresh?.invoke()
    }

    fun load(accountId: Long) {
        this.accountId.value = accountId
    }

}