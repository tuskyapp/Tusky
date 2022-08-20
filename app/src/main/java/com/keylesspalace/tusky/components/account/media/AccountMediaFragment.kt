/* Copyright 2022 Tusky Contributors
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

package com.keylesspalace.tusky.components.account.media

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import autodispose2.androidx.lifecycle.autoDispose
import com.bumptech.glide.Glide
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.ViewMediaActivity
import com.keylesspalace.tusky.components.account.AccountViewModel
import com.keylesspalace.tusky.databinding.FragmentTimelineBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.RefreshableFragment
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.ThemeUtils
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.openLink
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.view.SquareImageView
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.SingleObserver
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import retrofit2.Response
import java.io.IOException
import java.util.Random
import javax.inject.Inject

/**
 * Created by charlag on 26/10/2017.
 *
 * Fragment with multiple columns of media previews for the specified account.
 */

class AccountMediaFragment : Fragment(R.layout.fragment_timeline), RefreshableFragment, Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val binding by viewBinding(FragmentTimelineBinding::bind)

    private val viewModel: AccountMediaViewModel by viewModels { viewModelFactory }

    private val adapter = AccountMediaGridAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.accountId = arguments?.getString(ACCOUNT_ID_ARG)!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val columnCount = view.context.resources.getInteger(R.integer.profile_media_column_count)
        val imageSpacing = view.context.resources.getDimensionPixelSize(R.dimen.profile_media_spacing)

        val layoutManager = GridLayoutManager(view.context, columnCount)

        adapter.baseItemColor = ThemeUtils.getColor(view.context, android.R.attr.windowBackground)

        binding.recyclerView.addItemDecoration(GridSpacingItemDecoration(columnCount, imageSpacing, 0))

        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter

        binding.swipeRefreshLayout.isEnabled = false

        binding.statusView.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.media.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }
    }

    private fun viewMedia(items: List<AttachmentViewData>, currentIndex: Int, view: View?) {

        when (items[currentIndex].attachment.type) {
            Attachment.Type.IMAGE,
            Attachment.Type.GIFV,
            Attachment.Type.VIDEO,
            Attachment.Type.AUDIO -> {
                val intent = ViewMediaActivity.newIntent(context, items, currentIndex)
                if (view != null && activity != null) {
                    val url = items[currentIndex].attachment.url
                    ViewCompat.setTransitionName(view, url)
                    val options = ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(), view, url)
                    startActivity(intent, options.toBundle())
                } else {
                    startActivity(intent)
                }
            }
            Attachment.Type.UNKNOWN -> {
                context?.openLink(items[currentIndex].attachment.url)
            }
        }
    }

    override fun refreshContent() {
        adapter.refresh()
    }

    companion object {

        fun newInstance(accountId: String, enableSwipeToRefresh: Boolean = true): AccountMediaFragment {
            val fragment = AccountMediaFragment()
            val args = Bundle()
            args.putString(ACCOUNT_ID_ARG, accountId)
            fragment.arguments = args
            return fragment
        }

        private const val ACCOUNT_ID_ARG = "account_id"
        private const val TAG = "AccountMediaFragment"
    }
}
