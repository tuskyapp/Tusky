/* Copyright 2019 Conny Duck
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

package com.keylesspalace.tusky

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import com.google.android.material.transition.MaterialArcMotion
import com.google.android.material.transition.MaterialContainerTransform
import com.keylesspalace.tusky.adapter.ItemInteractionListener
import com.keylesspalace.tusky.adapter.TabAdapter
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.components.account.list.ListSelectionFragment
import com.keylesspalace.tusky.databinding.ActivityTabPreferenceBinding
import com.keylesspalace.tusky.entity.MastoList
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.ensureBottomPadding
import com.keylesspalace.tusky.util.unsafeLazy
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import com.keylesspalace.tusky.view.showHashtagPickerDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TabPreferenceActivity : BaseActivity(), ItemInteractionListener, ListSelectionFragment.ListSelectionListener {

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var eventHub: EventHub

    private val binding by viewBinding(ActivityTabPreferenceBinding::inflate)

    private lateinit var currentTabs: MutableList<TabData>
    private lateinit var currentTabsAdapter: TabAdapter
    private lateinit var touchHelper: ItemTouchHelper
    private lateinit var addTabAdapter: TabAdapter

    private val selectedItemElevation by unsafeLazy {
        resources.getDimension(R.dimen.selected_drag_item_elevation)
    }

    private val onFabDismissedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            toggleFab(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)

        supportActionBar?.apply {
            setTitle(R.string.title_tab_preferences)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.currentTabsRecyclerView.ensureBottomPadding(fab = true)
        ViewCompat.setOnApplyWindowInsetsListener(binding.actionButton) { _, insets ->
            val systemBarInsets = insets.getInsets(systemBars())
            val actionButtonMargin = resources.getDimensionPixelSize(R.dimen.fabMargin)
            binding.actionButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBarInsets.bottom + actionButtonMargin
            }
            binding.sheet.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBarInsets.bottom + actionButtonMargin
            }
            WindowInsetsCompat.Builder(insets)
                .setInsets(systemBars(), Insets.of(systemBarInsets.left, systemBarInsets.top, systemBarInsets.right, 0))
                .build()
        }

        currentTabs = accountManager.activeAccount?.tabPreferences.orEmpty().toMutableList()
        currentTabsAdapter = TabAdapter(currentTabs, false, this, currentTabs.size <= MIN_TAB_COUNT)
        binding.currentTabsRecyclerView.adapter = currentTabsAdapter
        binding.currentTabsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.currentTabsRecyclerView.addItemDecoration(
            DividerItemDecoration(this, LinearLayoutManager.VERTICAL)
        )

        addTabAdapter = TabAdapter(listOf(createTabDataFromId(DIRECT)), true, this)
        binding.addTabRecyclerView.adapter = addTabAdapter
        binding.addTabRecyclerView.layoutManager = LinearLayoutManager(this)

        touchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.END)
            }

            override fun isLongPressDragEnabled(): Boolean {
                return true
            }

            override fun isItemViewSwipeEnabled(): Boolean {
                return MIN_TAB_COUNT < currentTabs.size
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val temp = currentTabs[viewHolder.bindingAdapterPosition]
                currentTabs[viewHolder.bindingAdapterPosition] = currentTabs[target.bindingAdapterPosition]
                currentTabs[target.bindingAdapterPosition] = temp

                currentTabsAdapter.notifyItemMoved(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                saveTabs()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                onTabRemoved(viewHolder.bindingAdapterPosition)
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.elevation = selectedItemElevation
                }
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.elevation = 0f
            }
        })

        touchHelper.attachToRecyclerView(binding.currentTabsRecyclerView)

        binding.actionButton.setOnClickListener {
            toggleFab(true)
        }

        binding.scrim.setOnClickListener {
            toggleFab(false)
        }

        updateAvailableTabs()

        onBackPressedDispatcher.addCallback(onFabDismissedCallback)
    }

    override fun onTabAdded(tab: TabData) {
        toggleFab(false)

        if (tab.id == HASHTAG) {
            showAddHashtagDialog()
            return
        }

        if (tab.id == LIST) {
            showSelectListDialog()
            return
        }

        currentTabs.add(tab)
        currentTabsAdapter.notifyItemInserted(currentTabs.size - 1)
        updateAvailableTabs()
        saveTabs()
    }

    override fun onTabRemoved(position: Int) {
        currentTabs.removeAt(position)
        currentTabsAdapter.notifyItemRemoved(position)
        updateAvailableTabs()
        saveTabs()
    }

    override fun onActionChipClicked(tab: TabData, tabPosition: Int) {
        showAddHashtagDialog(tab, tabPosition)
    }

    override fun onChipClicked(tab: TabData, tabPosition: Int, chipPosition: Int) {
        val newArguments = tab.arguments.filterIndexed { i, _ -> i != chipPosition }
        val newTab = tab.copy(arguments = newArguments)
        currentTabs[tabPosition] = newTab
        saveTabs()

        currentTabsAdapter.notifyItemChanged(tabPosition)
    }

    private fun toggleFab(expand: Boolean) {
        val transition = MaterialContainerTransform().apply {
            startView = if (expand) binding.actionButton else binding.sheet
            val endView: View = if (expand) binding.sheet else binding.actionButton
            this.endView = endView
            addTarget(endView)
            scrimColor = Color.TRANSPARENT
            setPathMotion(MaterialArcMotion())
        }

        TransitionManager.beginDelayedTransition(binding.root, transition)
        binding.actionButton.visible(!expand)
        binding.sheet.visible(expand)
        binding.scrim.visible(expand)

        onFabDismissedCallback.isEnabled = expand
    }

    private fun showAddHashtagDialog(tab: TabData? = null, tabPosition: Int = 0) {
        showHashtagPickerDialog(mastodonApi, R.string.add_hashtag_title) { hashtag ->
            if (tab == null) {
                val newTab = createTabDataFromId(HASHTAG, listOf(hashtag))
                currentTabs.add(newTab)
                currentTabsAdapter.notifyItemInserted(currentTabs.size - 1)
            } else {
                val newTab = tab.copy(arguments = tab.arguments + hashtag)
                currentTabs[tabPosition] = newTab

                currentTabsAdapter.notifyItemChanged(tabPosition)
            }

            updateAvailableTabs()
            saveTabs()
        }
    }

    private var listSelectDialog: ListSelectionFragment? = null

    private fun showSelectListDialog() {
        listSelectDialog = ListSelectionFragment.newInstance(null)
        listSelectDialog?.show(supportFragmentManager, null)

        return
    }

    override fun onListSelected(list: MastoList) {
        listSelectDialog?.dismiss()
        listSelectDialog = null

        val newTab = createTabDataFromId(LIST, listOf(list.id, list.title))
        currentTabs.add(newTab)
        currentTabsAdapter.notifyItemInserted(currentTabs.size - 1)
        updateAvailableTabs()
        saveTabs()
    }

    private fun updateAvailableTabs() {
        val addableTabs: MutableList<TabData> = mutableListOf()

        val homeTab = createTabDataFromId(HOME)
        if (!currentTabs.contains(homeTab)) {
            addableTabs.add(homeTab)
        }
        val notificationTab = createTabDataFromId(NOTIFICATIONS)
        if (!currentTabs.contains(notificationTab)) {
            addableTabs.add(notificationTab)
        }
        val localTab = createTabDataFromId(LOCAL)
        if (!currentTabs.contains(localTab)) {
            addableTabs.add(localTab)
        }
        val federatedTab = createTabDataFromId(FEDERATED)
        if (!currentTabs.contains(federatedTab)) {
            addableTabs.add(federatedTab)
        }
        val directMessagesTab = createTabDataFromId(DIRECT)
        if (!currentTabs.contains(directMessagesTab)) {
            addableTabs.add(directMessagesTab)
        }
        val trendingTagsTab = createTabDataFromId(TRENDING_TAGS)
        if (!currentTabs.contains(trendingTagsTab)) {
            addableTabs.add(trendingTagsTab)
        }
        val bookmarksTab = createTabDataFromId(BOOKMARKS)
        if (!currentTabs.contains(bookmarksTab)) {
            addableTabs.add(bookmarksTab)
        }
        val trendingStatusesTab = createTabDataFromId(TRENDING_STATUSES)
        if (!currentTabs.contains(trendingStatusesTab)) {
            addableTabs.add(trendingStatusesTab)
        }

        addableTabs.add(createTabDataFromId(HASHTAG))
        addableTabs.add(createTabDataFromId(LIST))

        addTabAdapter.updateData(addableTabs)
        currentTabsAdapter.setRemoveButtonVisible(currentTabs.size > MIN_TAB_COUNT)
    }

    override fun onStartDelete(viewHolder: RecyclerView.ViewHolder) {
        touchHelper.startSwipe(viewHolder)
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        touchHelper.startDrag(viewHolder)
    }

    private fun saveTabs() {
        accountManager.activeAccount?.let {
            lifecycleScope.launch {
                accountManager.updateAccount(it) { copy(tabPreferences = currentTabs) }
            }
        }
    }

    companion object {
        private const val MIN_TAB_COUNT = 2
    }
}
