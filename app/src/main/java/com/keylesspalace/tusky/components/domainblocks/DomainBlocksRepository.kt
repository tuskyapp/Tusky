/*
 * Copyright 2023 Tusky Contributors
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.components.domainblocks

import androidx.paging.ExperimentalPagingApi
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import at.connyduck.calladapter.networkresult.NetworkResult
import at.connyduck.calladapter.networkresult.onSuccess
import com.keylesspalace.tusky.network.MastodonApi
import javax.inject.Inject

class DomainBlocksRepository @Inject constructor(
    private val api: MastodonApi
) {
    val domains: MutableList<String> = mutableListOf()
    var nextKey: String? = null

    private var factory = InvalidatingPagingSourceFactory {
        DomainBlocksPagingSource(domains.toList(), nextKey)
    }

    @OptIn(ExperimentalPagingApi::class)
    val domainPager = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = PAGE_SIZE
        ),
        remoteMediator = DomainBlocksRemoteMediator(api, this),
        pagingSourceFactory = factory
    ).flow

    /** Invalidate the active paging source, see [PagingSource.invalidate] */
    fun invalidate() {
        factory.invalidate()
    }

    suspend fun block(domain: String): NetworkResult<Unit> {
        return api.blockDomain(domain).onSuccess {
            domains.add(domain)
            factory.invalidate()
        }
    }

    suspend fun unblock(domain: String): NetworkResult<Unit> {
        return api.unblockDomain(domain).onSuccess {
            domains.remove(domain)
            factory.invalidate()
        }
    }

    companion object {
        private const val PAGE_SIZE = 20
    }
}
