/* Copyright 2017 Andrew Dawson
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import at.connyduck.sparkbutton.helpers.Utils
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.databinding.ActivityListsBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.MastoList
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.onTextChanged
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import com.keylesspalace.tusky.viewmodel.ListsViewModel
import com.keylesspalace.tusky.viewmodel.ListsViewModel.Event
import com.keylesspalace.tusky.viewmodel.ListsViewModel.LoadingState.ERROR_NETWORK
import com.keylesspalace.tusky.viewmodel.ListsViewModel.LoadingState.ERROR_OTHER
import com.keylesspalace.tusky.viewmodel.ListsViewModel.LoadingState.INITIAL
import com.keylesspalace.tusky.viewmodel.ListsViewModel.LoadingState.LOADED
import com.keylesspalace.tusky.viewmodel.ListsViewModel.LoadingState.LOADING
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Created by charlag on 1/4/18.
 */

class ListsActivity : BaseActivity(), Injectable, HasAndroidInjector {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>

    private val viewModel: ListsViewModel by viewModels { viewModelFactory }

    private val binding by viewBinding(ActivityListsBinding::inflate)

    private val adapter = ListsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.title_lists)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.listsRecycler.adapter = adapter
        binding.listsRecycler.layoutManager = LinearLayoutManager(this)
        binding.listsRecycler.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )

        lifecycleScope.launch {
            viewModel.state.collect(this@ListsActivity::update)
        }

        viewModel.retryLoading()

        binding.addListButton.setOnClickListener {
            showlistNameDialog(null)
        }

        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    Event.CREATE_ERROR -> showMessage(R.string.error_create_list)
                    Event.RENAME_ERROR -> showMessage(R.string.error_rename_list)
                    Event.DELETE_ERROR -> showMessage(R.string.error_delete_list)
                }
            }
        }
    }

    private fun showlistNameDialog(list: MastoList?) {
        val layout = FrameLayout(this)
        val editText = EditText(this)
        editText.setHint(R.string.hint_list_name)
        layout.addView(editText)
        val margin = Utils.dpToPx(this, 8)
        (editText.layoutParams as ViewGroup.MarginLayoutParams)
            .setMargins(margin, margin, margin, 0)

        val dialog = AlertDialog.Builder(this)
            .setView(layout)
            .setPositiveButton(
                if (list == null) R.string.action_create_list
                else R.string.action_rename_list
            ) { _, _ ->
                onPickedDialogName(editText.text, list?.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        val positiveButton = dialog.getButton(Dialog.BUTTON_POSITIVE)
        editText.onTextChanged { s, _, _, _ ->
            positiveButton.isEnabled = s.isNotBlank()
        }
        editText.setText(list?.title)
        editText.text?.let { editText.setSelection(it.length) }
    }

    private fun showListDeleteDialog(list: MastoList) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.dialog_delete_list_warning, list.title))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteList(list.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun update(state: ListsViewModel.State) {
        adapter.submitList(state.lists)
        binding.progressBar.visible(state.loadingState == LOADING)
        when (state.loadingState) {
            INITIAL, LOADING -> binding.messageView.hide()
            ERROR_NETWORK -> {
                binding.messageView.show()
                binding.messageView.setup(R.drawable.elephant_offline, R.string.error_network) {
                    viewModel.retryLoading()
                }
            }
            ERROR_OTHER -> {
                binding.messageView.show()
                binding.messageView.setup(R.drawable.elephant_error, R.string.error_generic) {
                    viewModel.retryLoading()
                }
            }
            LOADED ->
                if (state.lists.isEmpty()) {
                    binding.messageView.show()
                    binding.messageView.setup(
                        R.drawable.elephant_friend_empty, R.string.message_empty,
                        null
                    )
                } else {
                    binding.messageView.hide()
                }
        }
    }

    private fun showMessage(@StringRes messageId: Int) {
        Snackbar.make(
            binding.listsRecycler, messageId, Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun onListSelected(listId: String, listTitle: String) {
        startActivityWithSlideInAnimation(
            StatusListActivity.newListIntent(this, listId, listTitle)
        )
    }

    private fun openListSettings(list: MastoList) {
        AccountsInListFragment.newInstance(list.id, list.title).show(supportFragmentManager, null)
    }

    private fun renameListDialog(list: MastoList) {
        showlistNameDialog(list)
    }

    private fun onMore(list: MastoList, view: View) {
        PopupMenu(view.context, view).apply {
            inflate(R.menu.list_actions)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.list_edit -> openListSettings(list)
                    R.id.list_rename -> renameListDialog(list)
                    R.id.list_delete -> showListDeleteDialog(list)
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            show()
        }
    }

    private object ListsDiffer : DiffUtil.ItemCallback<MastoList>() {
        override fun areItemsTheSame(oldItem: MastoList, newItem: MastoList): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MastoList, newItem: MastoList): Boolean {
            return oldItem == newItem
        }
    }

    private inner class ListsAdapter :
        ListAdapter<MastoList, ListsAdapter.ListViewHolder>(ListsDiffer) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
            return LayoutInflater.from(parent.context).inflate(R.layout.item_list, parent, false)
                .let(this::ListViewHolder)
                .apply {
                    val iconColor = MaterialColors.getColor(nameTextView, android.R.attr.textColorTertiary)
                    val context = nameTextView.context
                    val icon = IconicsDrawable(context, GoogleMaterial.Icon.gmd_list).apply { sizeDp = 20; colorInt = iconColor }

                    nameTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
                }
        }

        override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
            holder.nameTextView.text = getItem(position).title
        }

        private inner class ListViewHolder(view: View) :
            RecyclerView.ViewHolder(view),
            View.OnClickListener {
            val nameTextView: TextView = view.findViewById(R.id.list_name_textview)
            val moreButton: ImageButton = view.findViewById(R.id.editListButton)

            init {
                view.setOnClickListener(this)
                moreButton.setOnClickListener(this)
            }

            override fun onClick(v: View) {
                if (v == itemView) {
                    val list = getItem(bindingAdapterPosition)
                    onListSelected(list.id, list.title)
                } else {
                    onMore(getItem(bindingAdapterPosition), v)
                }
            }
        }
    }

    private fun onPickedDialogName(name: CharSequence, listId: String?) {
        if (listId == null) {
            viewModel.createNewList(name.toString())
        } else {
            viewModel.renameList(listId, name.toString())
        }
    }

    override fun androidInjector() = dispatchingAndroidInjector

    companion object {
        fun newIntent(context: Context) = Intent(context, ListsActivity::class.java)
    }
}
