/* Copyright 2019 Joel Pyska
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

import androidx.lifecycle.LiveData
import androidx.paging.PagedList
import androidx.paging.PagedListAdapter
import com.keylesspalace.tusky.components.search.adapter.SearchAccountsAdapter
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.util.NetworkState

class SearchAccountsFragment : SearchFragment<Account>() {
    override fun createAdapter(): PagedListAdapter<Account, *> = SearchAccountsAdapter(this)

    override val networkStateRefresh: LiveData<NetworkState>
        get() = viewModel.networkStateAccountRefresh
    override val networkState: LiveData<NetworkState>
        get() = viewModel.networkStateAccount
    override val data: LiveData<PagedList<Account>>
        get() = viewModel.accounts

    companion object {
        fun newInstance() = SearchAccountsFragment()
    }

}
