/* Copyright 2019 Conny Duck
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import tech.bigfig.roma.adapter.ItemInteractionListener
import tech.bigfig.roma.adapter.TabAdapter
import tech.bigfig.roma.appstore.EventHub
import tech.bigfig.roma.appstore.MainTabsChangedEvent
import tech.bigfig.roma.di.Injectable
import tech.bigfig.roma.util.visible
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_tab_preference.*
import kotlinx.android.synthetic.main.toolbar_basic.*
import java.util.regex.Pattern
import javax.inject.Inject

import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from
import com.uber.autodispose.autoDisposable
import kotlinx.android.synthetic.main.activity_tab_preference.*

import kotlinx.android.synthetic.main.toolbar_basic.*

class TabPreferenceActivity : BaseActivity(), Injectable, ItemInteractionListener {

    @Inject
    lateinit var eventHub: EventHub

    private lateinit var currentTabs: MutableList<TabData>
    private lateinit var currentTabsAdapter: TabAdapter
    private lateinit var touchHelper: ItemTouchHelper
    private lateinit var addTabAdapter: TabAdapter

    private var tabsChanged = false

    private val selectedItemElevation by lazy { resources.getDimension(R.dimen.selected_drag_item_elevation) }

    private val hashtagRegex by lazy { Pattern.compile("([\\w_]*[\\p{Alpha}_][\\w_]*)", Pattern.CASE_INSENSITIVE) }

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

        touchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
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
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
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

        maxTabsInfo.text = getString(R.string.max_tab_number_reached, MAX_TAB_COUNT)

        updateAvailableTabs()

    }

    override fun onTabAdded(tab: TabData) {

        if (currentTabs.size >= MAX_TAB_COUNT) {
            return
        }

        actionButton.isExpanded = false

        if (tab.id == HASHTAG) {
            showEditHashtagDialog()
            return
        }

        currentTabs.add(tab)
        currentTabsAdapter.notifyItemInserted(currentTabs.size - 1)
        updateAvailableTabs()
        saveTabs()
    }

    override fun onActionChipClicked(tab: TabData) {
        showEditHashtagDialog(tab)
    }

    private fun showEditHashtagDialog(tab: TabData? = null) {

        val editText = AppCompatEditText(this)
        editText.setHint(R.string.edit_hashtag_hint)
        editText.setText("")
        editText.append(tab?.arguments?.first().orEmpty())

        val dialog = AlertDialog.Builder(this)
                .setTitle(R.string.edit_hashtag_title)
                .setView(editText)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_save) { _, _ ->
                    val input = editText.text.toString().trim()
                    if (tab == null) {
                        val newTab = createTabDataFromId(HASHTAG, listOf(input))
                        currentTabs.add(newTab)
                        currentTabsAdapter.notifyItemInserted(currentTabs.size - 1)
                    } else {
                        val newTab = tab.copy(arguments = listOf(input))
                        val position = currentTabs.indexOf(tab)
                        currentTabs[position] = newTab

                        currentTabsAdapter.notifyItemChanged(position)
                    }

                    updateAvailableTabs()
                    saveTabs()
                }
                .create()

        editText.onTextChanged { s, _, _, _ ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = validateHashtag(s)
        }

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = validateHashtag(editText.text)
        editText.requestFocus()
    }

    private fun validateHashtag(input: CharSequence?): Boolean {
        val trimmedInput = input?.trim() ?: ""
        return trimmedInput.isNotEmpty() && hashtagRegex.matcher(trimmedInput).matches()
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

        addableTabs.add(createTabDataFromId(HASHTAG))

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
        tabsChanged = true
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
        if (tabsChanged) {
            eventHub.dispatch(MainTabsChangedEvent(currentTabs))
        }
    }

    companion object {
        private const val MIN_TAB_COUNT = 2
        private const val MAX_TAB_COUNT = 5
    }

}
