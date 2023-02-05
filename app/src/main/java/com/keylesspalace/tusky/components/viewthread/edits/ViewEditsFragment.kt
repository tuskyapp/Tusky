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

package com.keylesspalace.tusky.components.viewthread.edits

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.google.android.material.color.MaterialColors
import com.keylesspalace.tusky.BottomSheetActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.StatusListActivity
import com.keylesspalace.tusky.components.account.AccountActivity
import com.keylesspalace.tusky.databinding.FragmentViewThreadBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

class ViewEditsFragment :
    Fragment(R.layout.fragment_view_thread),
    LinkListener,
    OnRefreshListener,
    MenuProvider,
    Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: ViewEditsViewModel by viewModels { viewModelFactory }

    private val binding by viewBinding(FragmentViewThreadBinding::bind)

    private lateinit var statusId: String

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)

        val divider = DividerItemDecoration(context, LinearLayout.VERTICAL)
        binding.recyclerView.addItemDecoration(divider)
        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        statusId = requireArguments().getString(STATUS_ID_EXTRA)!!
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val animateAvatars = preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false)
        val animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
        val useBlurhash = preferences.getBoolean(PrefKeys.USE_BLURHASH, true)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { uiState ->
                when (uiState) {
                    EditsUiState.Initial -> {}
                    EditsUiState.Loading -> {
                        binding.recyclerView.hide()
                        binding.statusView.hide()
                        binding.initialProgressBar.show()
                    }
                    EditsUiState.Refreshing -> {}
                    is EditsUiState.Error -> {
                        Log.w(TAG, "failed to load edits", uiState.throwable)

                        binding.swipeRefreshLayout.isRefreshing = false
                        binding.recyclerView.hide()
                        binding.statusView.show()
                        binding.initialProgressBar.hide()

                        if (uiState.throwable is IOException) {
                            binding.statusView.setup(R.drawable.elephant_offline, R.string.error_network) {
                                viewModel.loadEdits(statusId, force = true)
                            }
                        } else {
                            binding.statusView.setup(R.drawable.elephant_error, R.string.error_generic) {
                                viewModel.loadEdits(statusId, force = true)
                            }
                        }
                    }
                    is EditsUiState.Success -> {
                        binding.swipeRefreshLayout.isRefreshing = false
                        binding.recyclerView.show()
                        binding.statusView.hide()
                        binding.initialProgressBar.hide()

                        binding.recyclerView.adapter = ViewEditsAdapter(
                            edits = uiState.edits,
                            animateAvatars = animateAvatars,
                            animateEmojis = animateEmojis,
                            useBlurhash = useBlurhash,
                            listener = this@ViewEditsFragment
                        )
                    }
                }
            }
        }

        viewModel.loadEdits(statusId)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_view_edits, menu)
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
                onRefresh()
                true
            }
            else -> false
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().title = getString(R.string.title_edits)
    }

    override fun onRefresh() {
        viewModel.loadEdits(statusId, force = true, refreshing = true)
    }

    override fun onViewAccount(id: String) {
        bottomSheetActivity?.startActivityWithSlideInAnimation(AccountActivity.getIntent(requireContext(), id))
    }

    override fun onViewTag(tag: String) {
        bottomSheetActivity?.startActivityWithSlideInAnimation(StatusListActivity.newHashtagIntent(requireContext(), tag))
    }

    override fun onViewUrl(url: String) {
        bottomSheetActivity?.viewUrl(url)
    }

    private val bottomSheetActivity
        get() = (activity as? BottomSheetActivity)

    companion object {
        private const val TAG = "ViewEditsFragment"

        private const val STATUS_ID_EXTRA = "id"

        fun newInstance(statusId: String): ViewEditsFragment {
            val arguments = Bundle(1)
            val fragment = ViewEditsFragment()
            arguments.putString(STATUS_ID_EXTRA, statusId)
            fragment.arguments = arguments
            return fragment
        }
    }
}
