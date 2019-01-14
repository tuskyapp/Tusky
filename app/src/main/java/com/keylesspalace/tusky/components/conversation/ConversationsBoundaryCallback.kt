/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.keylesspalace.tusky.components.conversation

import androidx.annotation.MainThread
import androidx.paging.PagedList
import com.keylesspalace.tusky.entity.Conversation
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.PagingRequestHelper
import com.keylesspalace.tusky.util.createStatusLiveData
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.Executor

/**
 * This boundary callback gets notified when user reaches to the edges of the list such that the
 * database cannot provide any more data.
 * <p>
 * The boundary callback might be called multiple times for the same direction so it does its own
 * rate limiting using the PagingRequestHelper class.
 */
class ConversationsBoundaryCallback(
        private val accountId: Long,
        private val mastodonApi: MastodonApi,
        private val handleResponse: (Long, List<Conversation>?) -> Unit,
        private val ioExecutor: Executor,
        private val networkPageSize: Int)
    : PagedList.BoundaryCallback<ConversationEntity>() {

    val helper = PagingRequestHelper(ioExecutor)
    val networkState = helper.createStatusLiveData()

    /**
     * Database returned 0 items. We should query the backend for more items.
     */
    @MainThread
    override fun onZeroItemsLoaded() {
        helper.runIfNotRunning(PagingRequestHelper.RequestType.INITIAL) {
            mastodonApi.getConversations(null, networkPageSize)
                    .enqueue(createWebserviceCallback(it))

        }
    }

    /**
     * User reached to the end of the list.
     */
    @MainThread
    override fun onItemAtEndLoaded(itemAtEnd: ConversationEntity) {
        helper.runIfNotRunning(PagingRequestHelper.RequestType.AFTER) {
            mastodonApi.getConversations(itemAtEnd.id, networkPageSize)
                    .enqueue(createWebserviceCallback(it))
        }
    }

    /**
     * every time it gets new items, boundary callback simply inserts them into the database and
     * paging library takes care of refreshing the list if necessary.
     */
    private fun insertItemsIntoDb(
            response: Response<List<Conversation>>,
            it: PagingRequestHelper.Request.Callback) {
        ioExecutor.execute {
            handleResponse(accountId, response.body())
            it.recordSuccess()
        }
    }

    override fun onItemAtFrontLoaded(itemAtFront: ConversationEntity) {
        // ignored, since we only ever append to what's in the DB
    }

    private fun createWebserviceCallback(it: PagingRequestHelper.Request.Callback): Callback<List<Conversation>> {
        return object : Callback<List<Conversation>> {
            override fun onFailure(call: Call<List<Conversation>>, t: Throwable) {
                it.recordFailure(t)
            }

            override fun onResponse(call: Call<List<Conversation>>, response: Response<List<Conversation>>) {
                insertItemsIntoDb(response, it)
            }
        }
    }
}