/* Copyright Tusky Contributors
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

package com.keylesspalace.tusky.components.account.list

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.ListsActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.FragmentListsListBinding
import com.keylesspalace.tusky.databinding.ItemListBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.MastoList
import com.keylesspalace.tusky.util.BindingHolder
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.visible
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class ListSelectionFragment : DialogFragment(), Injectable {

    interface ListSelectionListener {
        fun onListSelected(list: MastoList)
    }

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: ListsForAccountViewModel by viewModels { viewModelFactory }

    private var _binding: FragmentListsListBinding? = null

    // This property is only valid between onCreateDialog and onDestroyView
    private val binding get() = _binding!!

    private val adapter = Adapter()

    private var selectListener: ListSelectionListener? = null
    private var accountId: String? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        selectListener = context as? ListSelectionListener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.TuskyDialogFragmentStyle)
        accountId = requireArguments().getString(ARG_ACCOUNT_ID)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        _binding = FragmentListsListBinding.inflate(layoutInflater)
        binding.listsView.adapter = adapter

        val dialogBuilder = AlertDialog.Builder(context)
            .setView(binding.root)
            .setTitle(R.string.select_list_title)
            .setNeutralButton(R.string.select_list_manage) { _, _ ->
                val listIntent = Intent(context, ListsActivity::class.java)
                startActivity(listIntent)
            }
            .setNegativeButton(if (accountId != null) R.string.button_done else android.R.string.cancel, null)

        val dialog = dialogBuilder.create()

        val showProgressBarJob = getProgressBarJob(binding.progressBar, 500)
        showProgressBarJob.start()

        // TODO change this to a (single) LoadState like elsewhere?
        lifecycleScope.launch {
            viewModel.states.collectLatest { states ->
                binding.progressBar.hide()
                showProgressBarJob.cancel()
                if (states.isEmpty()) {
                    binding.messageView.show()
                    binding.messageView.setup(R.drawable.elephant_friend_empty, R.string.no_lists)
                } else {
                    binding.listsView.show()
                    adapter.submitList(states)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.loadError.collectLatest { error ->
                Log.e(TAG, "failed to load lists", error)
                binding.progressBar.hide()
                showProgressBarJob.cancel()
                binding.listsView.hide()
                binding.messageView.apply {
                    show()
                    setup(error) { load() }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.actionError.collectLatest { error ->
                when (error.type) {
                    ActionError.Type.ADD -> {
                        Snackbar.make(binding.root, R.string.failed_to_add_to_list, Snackbar.LENGTH_LONG)
                            .setAction(R.string.action_retry) {
                                viewModel.addAccountToList(accountId!!, error.listId)
                            }
                            .show()
                    }
                    ActionError.Type.REMOVE -> {
                        Snackbar.make(binding.root, R.string.failed_to_remove_from_list, Snackbar.LENGTH_LONG)
                            .setAction(R.string.action_retry) {
                                viewModel.removeAccountFromList(accountId!!, error.listId)
                            }
                            .show()
                    }
                }
            }
        }

        lifecycleScope.launch {
            load()
        }

        return dialog
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun load() {
        binding.progressBar.show()
        binding.listsView.hide()
        binding.messageView.hide()
        viewModel.load(accountId)
    }

    private object Differ : DiffUtil.ItemCallback<AccountListState>() {
        override fun areItemsTheSame(
            oldItem: AccountListState,
            newItem: AccountListState
        ): Boolean {
            return oldItem.list.id == newItem.list.id
        }

        override fun areContentsTheSame(
            oldItem: AccountListState,
            newItem: AccountListState
        ): Boolean {
            return oldItem == newItem
        }
    }

    inner class Adapter :
        ListAdapter<AccountListState, BindingHolder<ItemListBinding>>(Differ) {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): BindingHolder<ItemListBinding> {
            return BindingHolder(ItemListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: BindingHolder<ItemListBinding>, position: Int) {
            val item = getItem(position)
            holder.binding.listName.text = item.list.title
            accountId?.let { accountId ->
                holder.binding.addButton.apply {
                    visible(!item.includesAccount)
                    setOnClickListener {
                        viewModel.addAccountToList(accountId, item.list.id)
                    }
                }
                holder.binding.removeButton.apply {
                    visible(item.includesAccount)
                    setOnClickListener {
                        viewModel.removeAccountFromList(accountId, item.list.id)
                    }
                }
            }

            holder.itemView.setOnClickListener {
                selectListener?.onListSelected(item.list)

                accountId?.let { accountId ->
                    if (item.includesAccount) {
                        viewModel.removeAccountFromList(accountId, item.list.id)
                    } else {
                        viewModel.addAccountToList(accountId, item.list.id)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "ListsListFragment"
        private const val ARG_ACCOUNT_ID = "accountId"

        fun newInstance(accountId: String?): ListSelectionFragment {
            val args = Bundle().apply {
                putString(ARG_ACCOUNT_ID, accountId)
            }
            return ListSelectionFragment().apply { arguments = args }
        }
    }
}
