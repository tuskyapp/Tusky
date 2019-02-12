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

import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.adapter.ItemInteractionListener
import com.keylesspalace.tusky.adapter.TabAdapter
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.MainTabsChangedEvent
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.util.visible
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_tab_preference.*
import kotlinx.android.synthetic.main.toolbar_basic.*
import javax.inject.Inject

import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from
import com.uber.autodispose.autoDisposable

class TabPreferenceActivity : BaseActivity(), Injectable, ItemInteractionListener {

    @Inject
    lateinit var eventHub: EventHub

    private lateinit var currentTabs: MutableList<TabData>
    private lateinit var currentTabsAdapter: TabAdapter
    private lateinit var touchHelper: ItemTouchHelper
    private lateinit var addTabAdapter: TabAdapter

    private val selectedItemElevation by lazy { resources.getDimension(R.dimen.selected_drag_item_elevation) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_tab_preference)

        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            setTitle(R.string.title_tab_preferences)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        currentTabs = (accountManager.activeAccount?.tabPreferences ?: emptyList()).toMutableList()
        currentTabsAdapter = TabAdapter(currentTabs, false, this)
        currentTabsRecyclerView.adapter = currentTabsAdapter
        currentTabsRecyclerView.layoutManager = LinearLayoutManager(this)
        currentTabsRecyclerView.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))

        addTabAdapter = TabAdapter(listOf(createTabDataFromId(DIRECT)), true, this)
        addTabRecyclerView.adapter = addTabAdapter
        addTabRecyclerView.layoutManager = LinearLayoutManager(this)

        touchHelper = ItemTouchHelper(object: ItemTouchHelper.Callback(){
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.END)
            }

            override fun isLongPressDragEnabled(): Boolean {
                return true
            }

            override fun isItemViewSwipeEnabled(): Boolean {
                return MIN_TAB_COUNT < currentTabs.size
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val temp = currentTabs[viewHolder.adapterPosition]
                currentTabs[viewHolder.adapterPosition] = currentTabs[target.adapterPosition]
                currentTabs[target.adapterPosition] = temp

                currentTabsAdapter.notifyItemMoved(viewHolder.adapterPosition, target.adapterPosition)
                saveTabs()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                currentTabs.removeAt(viewHolder.adapterPosition)
                currentTabsAdapter.notifyItemRemoved(viewHolder.adapterPosition)
                updateAvailableTabs()
                saveTabs()
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                if(actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.elevation = selectedItemElevation
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.elevation = 0f
            }
        })

        touchHelper.attachToRecyclerView(currentTabsRecyclerView)


        actionButton.setOnClickListener {
            actionButton.isExpanded = true
        }

        scrim.setOnClickListener {
            actionButton.isExpanded = false
        }

        updateAvailableTabs()

    }

    override fun onTabAdded(tab: TabData) {
        currentTabs.add(tab)
        currentTabsAdapter.notifyItemInserted(currentTabs.size - 1)
        actionButton.isExpanded = false
        updateAvailableTabs()
        saveTabs()
    }

    private fun updateAvailableTabs() {
        val addableTabs: MutableList<TabData> = mutableListOf()

        val homeTab = createTabDataFromId(HOME)
        if(!currentTabs.contains(homeTab)) {
            addableTabs.add(homeTab)
        }
        val notificationTab = createTabDataFromId(NOTIFICATIONS)
        if(!currentTabs.contains(notificationTab)) {
            addableTabs.add(notificationTab)
        }
        val localTab = createTabDataFromId(LOCAL)
        if(!currentTabs.contains(localTab)) {
            addableTabs.add(localTab)
        }
        val federatedTab = createTabDataFromId(FEDERATED)
        if(!currentTabs.contains(federatedTab)) {
            addableTabs.add(federatedTab)
        }
        val directMessagesTab = createTabDataFromId(DIRECT)
        if(!currentTabs.contains(directMessagesTab)) {
            addableTabs.add(directMessagesTab)
        }

        addTabAdapter.updateData(addableTabs)

        maxTabsInfo.visible(addableTabs.size == 0 || currentTabs.size >= MAX_TAB_COUNT)

    }

    override fun onStartDelete(viewHolder: RecyclerView.ViewHolder) {
        touchHelper.startSwipe(viewHolder)
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        touchHelper.startDrag(viewHolder)
    }

    private fun saveTabs() {
        accountManager.activeAccount?.let {
            Single.fromCallable {
                it.tabPreferences = currentTabs
                accountManager.saveAccount(it)
            }
                    .subscribeOn(Schedulers.io())
                    .autoDisposable(from(this, Lifecycle.Event.ON_DESTROY))
                    .subscribe()

        }
    }

    override fun onBackPressed() {
        if (actionButton.isExpanded) {
            actionButton.isExpanded = false
        } else {
            super.onBackPressed()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return false
    }

    override fun onPause() {
        super.onPause()
        eventHub.dispatch(MainTabsChangedEvent(currentTabs))
    }

    companion object {
        private const val MIN_TAB_COUNT = 2
        private const val MAX_TAB_COUNT = 5
    }

}
