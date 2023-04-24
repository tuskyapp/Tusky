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

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.updatePadding
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.sparkbutton.helpers.Utils
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialArcMotion
import com.google.android.material.transition.MaterialContainerTransform
import com.keylesspalace.tusky.adapter.ItemInteractionListener
import com.keylesspalace.tusky.adapter.ListSelectionAdapter
import com.keylesspalace.tusky.adapter.TabAdapter
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.MainTabsChangedEvent
import com.keylesspalace.tusky.databinding.ActivityTabPreferenceBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.getDimension
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.unsafeLazy
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import javax.inject.Inject

abstract class OrderableListPreferenceActivity :
    BaseActivity(),
    Injectable,
    ItemInteractionListener {

    abstract fun saveList(list: List<ScreenData>)
    abstract fun initializeList(): List<ScreenData>
    abstract fun getMinCount(): Int
    abstract fun getMaxCount(): Int
    abstract fun getActivityTitle(): CharSequence

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var eventHub: EventHub

    private val binding by viewBinding(ActivityTabPreferenceBinding::inflate)

    private lateinit var currentTabs: MutableList<ScreenData>
    private lateinit var currentTabsAdapter: TabAdapter
    private lateinit var touchHelper: ItemTouchHelper
    private lateinit var addTabAdapter: TabAdapter

    protected var tabsChanged = false

    private val selectedItemElevation by unsafeLazy { resources.getDimension(R.dimen.selected_drag_item_elevation) }

    private val hashtagRegex by unsafeLazy { Pattern.compile("([\\w_]*[\\p{Alpha}_][\\w_]*)", Pattern.CASE_INSENSITIVE) }

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
            setTitle(getActivityTitle())
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        currentTabs = initializeList().toMutableList()
        currentTabsAdapter =
            TabAdapter(currentTabs, false, this, currentTabs.size <= getMinCount())
        binding.currentTabsRecyclerView.adapter = currentTabsAdapter
        binding.currentTabsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.currentTabsRecyclerView.addItemDecoration(
            DividerItemDecoration(
                this,
                LinearLayoutManager.VERTICAL
            )
        )

        addTabAdapter = TabAdapter(listOf(createScreenDataFromId(DIRECT)), true, this)
        binding.addTabRecyclerView.adapter = addTabAdapter
        binding.addTabRecyclerView.layoutManager = LinearLayoutManager(this)

        touchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return makeMovementFlags(
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                    ItemTouchHelper.END
                )
            }

            override fun isLongPressDragEnabled(): Boolean {
                return true
            }

            override fun isItemViewSwipeEnabled(): Boolean {
                return getMinCount() < currentTabs.size
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val temp = currentTabs[viewHolder.bindingAdapterPosition]
                currentTabs[viewHolder.bindingAdapterPosition] =
                    currentTabs[target.bindingAdapterPosition]
                currentTabs[target.bindingAdapterPosition] = temp

                currentTabsAdapter.notifyItemMoved(
                    viewHolder.bindingAdapterPosition,
                    target.bindingAdapterPosition
                )
                saveList(currentTabs)
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

        binding.maxTabsInfo.text = resources.getQuantityString(
            R.plurals.max_tab_number_reached,
            getMaxCount(),
            getMaxCount()
        )

        updateAvailableTabs()

        onBackPressedDispatcher.addCallback(onFabDismissedCallback)
    }

    override fun onTabAdded(tab: ScreenData) {
        if (currentTabs.size >= getMaxCount()) {
            return
        }

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
        saveList(currentTabs)
    }

    override fun onTabRemoved(position: Int) {
        currentTabs.removeAt(position)
        currentTabsAdapter.notifyItemRemoved(position)
        updateAvailableTabs()
        saveList(currentTabs)
    }

    override fun onActionChipClicked(tab: ScreenData, tabPosition: Int) {
        showAddHashtagDialog(tab, tabPosition)
    }

    override fun onChipClicked(tab: ScreenData, tabPosition: Int, chipPosition: Int) {
        val newArguments = tab.arguments.filterIndexed { i, _ -> i != chipPosition }
        val newTab = tab.withArguments(newArguments)
        currentTabs[tabPosition] = newTab
        saveList(currentTabs)

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

    private fun showAddHashtagDialog(tab: ScreenData? = null, tabPosition: Int = 0) {
        val frameLayout = FrameLayout(this)
        val padding = Utils.dpToPx(this, 8)
        frameLayout.updatePadding(left = padding, right = padding)

        val editText = AppCompatEditText(this)
        editText.setHint(R.string.edit_hashtag_hint)
        editText.setText("")
        frameLayout.addView(editText)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.add_hashtag_title)
            .setView(frameLayout)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val input = editText.text.toString().trim()
                if (tab == null) {
                    val newTab = createScreenDataFromId(HASHTAG, listOf(input))
                    currentTabs.add(newTab)
                    currentTabsAdapter.notifyItemInserted(currentTabs.size - 1)
                } else {
                    val newTab = tab.withArguments(tab.arguments + input)
                    currentTabs[tabPosition] = newTab

                    currentTabsAdapter.notifyItemChanged(tabPosition)
                }

                updateAvailableTabs()
                saveList(currentTabs)
            }
            .create()

        editText.doOnTextChanged { s, _, _, _ ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = validateHashtag(s)
        }

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = validateHashtag(editText.text)
        editText.requestFocus()
    }

    private fun showSelectListDialog() {
        val adapter = ListSelectionAdapter(this)

        val statusLayout = LinearLayout(this)
        statusLayout.gravity = Gravity.CENTER
        val progress = ProgressBar(this)
        val preferredPadding = getDimension(this, androidx.appcompat.R.attr.dialogPreferredPadding)
        progress.setPadding(preferredPadding, 0, preferredPadding, 0)
        progress.visible(false)

        val noListsText = TextView(this)
        noListsText.setPadding(preferredPadding, 0, preferredPadding, 0)
        noListsText.text = getText(R.string.select_list_empty)
        noListsText.visible(false)

        statusLayout.addView(progress)
        statusLayout.addView(noListsText)

        val dialogBuilder = AlertDialog.Builder(this)
            .setTitle(R.string.select_list_title)
            .setNeutralButton(R.string.select_list_manage) { _, _ ->
                val listIntent = Intent(applicationContext, ListsActivity::class.java)
                startActivity(listIntent)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setView(statusLayout)
            .setAdapter(adapter) { _, position ->
                val list = adapter.getItem(position)
                val newTab = createScreenDataFromId(LIST, listOf(list!!.id, list.title))
                currentTabs.add(newTab)
                currentTabsAdapter.notifyItemInserted(currentTabs.size - 1)
                updateAvailableTabs()
                saveList(currentTabs)
            }

        val showProgressBarJob = getProgressBarJob(progress, 500)
        showProgressBarJob.start()

        val dialog = dialogBuilder.show()

        lifecycleScope.launch {
            mastodonApi.getLists().fold(
                { lists ->
                    showProgressBarJob.cancel()
                    adapter.addAll(lists)
                    if (lists.isEmpty()) {
                        noListsText.show()
                    }
                },
                { throwable ->
                    dialog.hide()
                    Log.e("TabPreferenceActivity", "failed to load lists", throwable)
                    Snackbar.make(binding.root, R.string.error_list_load, Snackbar.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun getProgressBarJob(progressView: View, delayMs: Long) = this.lifecycleScope.launch(
        start = CoroutineStart.LAZY
    ) {
        try {
            delay(delayMs)
            progressView.show()
            awaitCancellation()
        } finally {
            progressView.hide()
        }
    }

    private fun validateHashtag(input: CharSequence?): Boolean {
        val trimmedInput = input?.trim() ?: ""
        return trimmedInput.isNotEmpty() && hashtagRegex.matcher(trimmedInput).matches()
    }

    private fun updateAvailableTabs() {
        val addableTabs: MutableList<ScreenData> = mutableListOf()

        listOf(HOME, NOTIFICATIONS, LOCAL, FEDERATED, DIRECT, TRENDING)
            .map { id -> createScreenDataFromId(id) }
            .forEach { item ->
                if (!currentTabs.contains(item)) {
                    addableTabs.add(item)
                }
            }

        if(this is DrawerPreferenceActivity) {
            // Items from sidebar
            listOf(EDIT_PROFILE, FAVOURITES, BOOKMARKS, FOLLOW_REQUESTS, LISTS, DRAFTS, SCHEDULED_POSTS, ANNOUNCEMENTS)
                .map { id -> createScreenDataFromId(id) }
                .forEach { item ->
                if (!currentTabs.contains(item)) {
                    addableTabs.add(item)
                }
            }
        }

        listOf(HASHTAG, LIST)
            .map { id -> createScreenDataFromId(id) }
            .forEach { item ->
                if (!currentTabs.contains(item)) {
                    addableTabs.add(item)
                }
            }

        addTabAdapter.updateData(addableTabs)

        binding.maxTabsInfo.visible(addableTabs.isEmpty() || currentTabs.size >= getMaxCount())
        currentTabsAdapter.setRemoveButtonVisible(currentTabs.size > getMinCount())
    }

    override fun onStartDelete(viewHolder: RecyclerView.ViewHolder) {
        touchHelper.startSwipe(viewHolder)
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        touchHelper.startDrag(viewHolder)
    }

    override fun onPause() {
        super.onPause()
        if (tabsChanged) {
            lifecycleScope.launch {
                eventHub.dispatch(MainTabsChangedEvent(currentTabs))
            }
        }
    }
}
