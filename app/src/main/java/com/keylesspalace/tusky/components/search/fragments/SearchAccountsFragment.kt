/* Copyright 2021 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.components.search.fragments

import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import com.keylesspalace.tusky.components.search.adapter.SearchAccountsAdapter
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.settings.PrefStore
import com.keylesspalace.tusky.settings.get
import com.keylesspalace.tusky.settings.getBlocking
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchAccountsFragment : SearchFragment<TimelineAccount>() {
    @Inject
    lateinit var prefStore: PrefStore

    override fun createAdapter(): PagingDataAdapter<TimelineAccount, *> {
        return SearchAccountsAdapter(
            this,
            animateAvatars = prefStore.getBlocking().animateAvatars,
            animateEmojis = prefStore.getBlocking().animateEmojis,
        )
    }

    override val data: Flow<PagingData<TimelineAccount>>
        get() = viewModel.accountsFlow

    companion object {
        fun newInstance() = SearchAccountsFragment()
    }
}
