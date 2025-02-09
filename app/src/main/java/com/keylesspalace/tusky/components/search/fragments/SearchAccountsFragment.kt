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

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DividerItemDecoration
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder
import com.keylesspalace.tusky.components.search.adapter.SearchAccountsAdapter
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.updateRelativeTimePeriodically
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@AndroidEntryPoint
class SearchAccountsFragment : SearchFragment<TimelineAccount>() {

    @Inject
    lateinit var preferences: SharedPreferences

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.searchRecyclerView.addItemDecoration(
            DividerItemDecoration(
                binding.searchRecyclerView.context,
                DividerItemDecoration.VERTICAL
            )
        )

        adapter?.run {
            updateRelativeTimePeriodically(preferences) {
                notifyItemRangeChanged(0, itemCount, StatusBaseViewHolder.Key.KEY_CREATED)
            }
        }
    }

    override fun createAdapter(): PagingDataAdapter<TimelineAccount, *> {
        return SearchAccountsAdapter(
            this,
            preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false),
            preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false),
            preferences.getBoolean(PrefKeys.SHOW_BOT_OVERLAY, true)
        )
    }

    override val data: Flow<PagingData<TimelineAccount>>
        get() = viewModel.accountsFlow

    companion object {
        fun newInstance() = SearchAccountsFragment()
    }
}
