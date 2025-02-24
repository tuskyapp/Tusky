/* Copyright Tusky contributors
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
import android.view.WindowManager
import android.widget.PopupMenu
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.databinding.ActivityListsBinding
import com.keylesspalace.tusky.databinding.DialogListBinding
import com.keylesspalace.tusky.databinding.ItemListBinding
import com.keylesspalace.tusky.entity.MastoList
import com.keylesspalace.tusky.util.BindingHolder
import com.keylesspalace.tusky.util.ensureBottomMargin
import com.keylesspalace.tusky.util.ensureBottomPadding
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.startActivityWithSlideInAnimation
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import com.keylesspalace.tusky.viewmodel.ListsViewModel
import com.keylesspalace.tusky.viewmodel.ListsViewModel.Event
import com.keylesspalace.tusky.viewmodel.ListsViewModel.LoadingState.ERROR_NETWORK
import com.keylesspalace.tusky.viewmodel.ListsViewModel.LoadingState.ERROR_OTHER
import com.keylesspalace.tusky.viewmodel.ListsViewModel.LoadingState.INITIAL
import com.keylesspalace.tusky.viewmodel.ListsViewModel.LoadingState.LOADED
import com.keylesspalace.tusky.viewmodel.ListsViewModel.LoadingState.LOADING
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

// TODO use the ListSelectionFragment (and/or its adapter or binding) here; but keep the LoadingState from here (?)

@AndroidEntryPoint
class ListsActivity : BaseActivity() {

    private val viewModel: ListsViewModel by viewModels()

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

        binding.addListButton.ensureBottomMargin()
        binding.listsRecycler.ensureBottomPadding(fab = true)

        binding.listsRecycler.adapter = adapter
        binding.listsRecycler.layoutManager = LinearLayoutManager(this)
        binding.listsRecycler.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )

        binding.swipeRefreshLayout.setOnRefreshListener { viewModel.retryLoading() }

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
                    Event.UPDATE_ERROR -> showMessage(R.string.error_rename_list)
                    Event.DELETE_ERROR -> showMessage(R.string.error_delete_list)
                }
            }
        }
    }

    private fun showlistNameDialog(list: MastoList?) {
        var selectedReplyPolicyIndex = MastoList.ReplyPolicy.from(list?.repliesPolicy).ordinal

        val replyPolicies = resources.getStringArray(R.array.list_reply_policies_display)
        val binding = DialogListBinding.inflate(layoutInflater).apply {
            replyPolicyDropDown.setText(replyPolicies[selectedReplyPolicyIndex])
            replyPolicyDropDown.setSimpleItems(replyPolicies)
            replyPolicyDropDown.setOnItemClickListener { _, _, position, _ ->
                selectedReplyPolicyIndex = position
            }
        }
        val inset = resources.getDimensionPixelSize(R.dimen.dialog_inset)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(binding.root)
            .setBackgroundInsetTop(inset)
            .setBackgroundInsetEnd(inset)
            .setBackgroundInsetBottom(inset)
            .setBackgroundInsetStart(inset)
            .setPositiveButton(
                if (list == null) {
                    R.string.action_create_list
                } else {
                    R.string.action_rename_list
                }
            ) { _, _ ->
                onPickedDialogName(
                    binding.nameText.text.toString(),
                    list?.id,
                    binding.exclusiveCheckbox.isChecked,
                    MastoList.ReplyPolicy.entries[selectedReplyPolicyIndex].policy
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        // yes, SOFT_INPUT_ADJUST_RESIZE is deprecated, but without it the dropdown can get behind the keyboard
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        binding.nameText.let { editText ->
            editText.doOnTextChanged { s, _, _, _ ->
                dialog.getButton(Dialog.BUTTON_POSITIVE).isEnabled = s?.isNotBlank() == true
            }
            editText.setText(list?.title)
            editText.requestFocus()
            editText.text?.let { editText.setSelection(it.length) }
        }

        list?.let {
            if (it.exclusive == null) {
                binding.exclusiveCheckbox.visible(false)
            } else {
                binding.exclusiveCheckbox.isChecked = it.exclusive
            }
        }
    }

    private fun showListDeleteDialog(list: MastoList) {
        MaterialAlertDialogBuilder(this)
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
        binding.swipeRefreshLayout.isRefreshing = state.loadingState == LOADING
        when (state.loadingState) {
            INITIAL, LOADING -> binding.messageView.hide()
            ERROR_NETWORK -> {
                binding.messageView.show()
                binding.messageView.setup(R.drawable.errorphant_offline, R.string.error_network) {
                    viewModel.retryLoading()
                }
            }
            ERROR_OTHER -> {
                binding.messageView.show()
                binding.messageView.setup(R.drawable.errorphant_error, R.string.error_generic) {
                    viewModel.retryLoading()
                }
            }
            LOADED ->
                if (state.lists.isEmpty()) {
                    binding.messageView.show()
                    binding.messageView.setup(
                        R.drawable.elephant_friend_empty,
                        R.string.message_empty,
                        null
                    )
                    binding.messageView.showHelp(R.string.help_empty_lists)
                } else {
                    binding.messageView.hide()
                }
        }
    }

    private fun showMessage(@StringRes messageId: Int) {
        Snackbar.make(
            binding.listsRecycler,
            messageId,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun onListSelected(list: MastoList) {
        startActivityWithSlideInAnimation(
            StatusListActivity.newListIntent(this, list.id, list.title)
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
                    R.id.list_update -> renameListDialog(list)
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
        ListAdapter<MastoList, BindingHolder<ItemListBinding>>(ListsDiffer) {

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): BindingHolder<ItemListBinding> {
            return BindingHolder(ItemListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: BindingHolder<ItemListBinding>, position: Int) {
            val item = getItem(position)
            holder.binding.listName.text = item.title

            holder.binding.moreButton.apply {
                visible(true)
                setOnClickListener {
                    onMore(item, holder.binding.moreButton)
                }
            }

            holder.itemView.setOnClickListener {
                onListSelected(item)
            }
        }
    }

    private fun onPickedDialogName(
        name: String,
        listId: String?,
        exclusive: Boolean,
        replyPolicy: String
    ) {
        if (listId == null) {
            viewModel.createNewList(name, exclusive, replyPolicy)
        } else {
            viewModel.updateList(listId, name, exclusive, replyPolicy)
        }
    }

    companion object {
        fun newIntent(context: Context) = Intent(context, ListsActivity::class.java)
    }
}
