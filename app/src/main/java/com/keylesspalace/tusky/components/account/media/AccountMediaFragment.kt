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

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.color.MaterialColors
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.ViewMediaActivity
import com.keylesspalace.tusky.databinding.FragmentTimelineBinding
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.interfaces.RefreshableFragment
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.openLink
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/**
 * Fragment with multiple columns of media previews for the specified account.
 */
class AccountMediaFragment :
    Fragment(R.layout.fragment_timeline),
    RefreshableFragment,
    MenuProvider,
    Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var accountManager: AccountManager

    private val binding by viewBinding(FragmentTimelineBinding::bind)

    private val viewModel: AccountMediaViewModel by viewModels { viewModelFactory }

    private lateinit var adapter: AccountMediaGridAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.accountId = arguments?.getString(ACCOUNT_ID_ARG)!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        val alwaysShowSensitiveMedia = accountManager.activeAccount!!.alwaysShowSensitiveMedia

        val preferences = PreferenceManager.getDefaultSharedPreferences(view.context)
        val useBlurhash = preferences.getBoolean(PrefKeys.USE_BLURHASH, true)

        adapter = AccountMediaGridAdapter(
            alwaysShowSensitiveMedia = alwaysShowSensitiveMedia,
            useBlurhash = useBlurhash,
            context = view.context,
            onAttachmentClickListener = ::onAttachmentClick
        )

        val columnCount = view.context.resources.getInteger(R.integer.profile_media_column_count)
        val imageSpacing = view.context.resources.getDimensionPixelSize(R.dimen.profile_media_spacing)

        binding.recyclerView.addItemDecoration(GridSpacingItemDecoration(columnCount, imageSpacing, 0))

        binding.recyclerView.layoutManager = GridLayoutManager(view.context, columnCount)
        binding.recyclerView.adapter = adapter

        binding.swipeRefreshLayout.isEnabled = false
        binding.swipeRefreshLayout.setOnRefreshListener { refreshContent() }
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)

        binding.statusView.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.media.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        adapter.addLoadStateListener { loadState ->
            binding.statusView.hide()
            binding.progressBar.hide()

            if (loadState.refresh != LoadState.Loading && loadState.source.refresh != LoadState.Loading) {
                binding.swipeRefreshLayout.isRefreshing = false
            }

            if (adapter.itemCount == 0) {
                when (loadState.refresh) {
                    is LoadState.NotLoading -> {
                        if (loadState.append is LoadState.NotLoading && loadState.source.refresh is LoadState.NotLoading) {
                            binding.statusView.show()
                            binding.statusView.setup(R.drawable.elephant_friend_empty, R.string.message_empty, null)
                        }
                    }
                    is LoadState.Error -> {
                        binding.statusView.show()

                        if ((loadState.refresh as LoadState.Error).error is IOException) {
                            binding.statusView.setup(R.drawable.elephant_offline, R.string.error_network, null)
                        } else {
                            binding.statusView.setup(R.drawable.elephant_error, R.string.error_generic, null)
                        }
                    }
                    is LoadState.Loading -> {
                        binding.progressBar.show()
                    }
                }
            }
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_account_media, menu)
        menu.findItem(R.id.action_refresh)?.apply {
            icon = IconicsDrawable(requireContext(), GoogleMaterial.Icon.gmd_refresh).apply {
                sizeDp = 20
                colorInt = MaterialColors.getColor(binding.root, android.R.attr.textColorPrimary)
            }
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_refresh -> {
                binding.swipeRefreshLayout.isRefreshing = true
                refreshContent()
                true
            }
            else -> false
        }
    }

    private fun onAttachmentClick(selected: AttachmentViewData, view: View) {
        if (!selected.isRevealed) {
            viewModel.revealAttachment(selected)
            return
        }
        val attachmentsFromSameStatus = viewModel.attachmentData.filter { attachmentViewData ->
            attachmentViewData.statusId == selected.statusId
        }
        val currentIndex = attachmentsFromSameStatus.indexOf(selected)

        when (selected.attachment.type) {
            Attachment.Type.IMAGE,
            Attachment.Type.GIFV,
            Attachment.Type.VIDEO,
            Attachment.Type.AUDIO -> {
                val intent = ViewMediaActivity.newIntent(context, attachmentsFromSameStatus, currentIndex)
                if (activity != null) {
                    val url = selected.attachment.url
                    ViewCompat.setTransitionName(view, url)
                    val options = ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(), view, url)
                    startActivity(intent, options.toBundle())
                } else {
                    startActivity(intent)
                }
            }
            Attachment.Type.UNKNOWN -> {
                context?.openLink(selected.attachment.url)
            }
        }
    }

    override fun refreshContent() {
        adapter.refresh()
    }

    companion object {

        fun newInstance(accountId: String): AccountMediaFragment {
            val fragment = AccountMediaFragment()
            val args = Bundle(1)
            args.putString(ACCOUNT_ID_ARG, accountId)
            fragment.arguments = args
            return fragment
        }

        private const val ACCOUNT_ID_ARG = "account_id"
        private const val TAG = "AccountMediaFragment"
    }
}
