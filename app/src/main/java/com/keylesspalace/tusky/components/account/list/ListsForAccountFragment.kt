/* Copyright 2022 kyori19
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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.FragmentListsForAccountBinding
import com.keylesspalace.tusky.databinding.ItemAddOrRemoveFromListBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.util.BindingHolder
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

class ListsForAccountFragment : DialogFragment(), Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: ListsForAccountViewModel by viewModels { viewModelFactory }
    private val binding by viewBinding(FragmentListsForAccountBinding::bind)

    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.TuskyDialogFragmentStyle)

        viewModel.setup(requireArguments().getString(ARG_ACCOUNT_ID)!!)
    }

    override fun onStart() {
        super.onStart()
        dialog?.apply {
            window?.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_lists_for_account, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.listsView.layoutManager = LinearLayoutManager(view.context)
        binding.listsView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.states.collectLatest { states ->
                binding.progressBar.hide()
                if (states.isEmpty()) {
                    binding.messageView.show()
                    binding.messageView.setup(R.drawable.elephant_friend_empty, R.string.no_lists) {
                        load()
                    }
                } else {
                    binding.listsView.show()
                    adapter.submitList(states)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.loadError.collectLatest { error ->
                binding.progressBar.hide()
                binding.listsView.hide()
                binding.messageView.apply {
                    show()

                    if (error is IOException) {
                        setup(R.drawable.elephant_offline, R.string.error_network) {
                            load()
                        }
                    } else {
                        setup(R.drawable.elephant_error, R.string.error_generic) {
                            load()
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.actionError.collectLatest { error ->
                when (error.type) {
                    ActionError.Type.ADD -> {
                        Snackbar.make(binding.root, R.string.failed_to_add_to_list, Snackbar.LENGTH_LONG)
                            .setAction(R.string.action_retry) {
                                viewModel.addAccountToList(error.listId)
                            }
                            .show()
                    }
                    ActionError.Type.REMOVE -> {
                        Snackbar.make(binding.root, R.string.failed_to_remove_from_list, Snackbar.LENGTH_LONG)
                            .setAction(R.string.action_retry) {
                                viewModel.removeAccountFromList(error.listId)
                            }
                            .show()
                    }
                }
            }
        }

        binding.doneButton.setOnClickListener {
            dismiss()
        }

        load()
    }

    private fun load() {
        binding.progressBar.show()
        binding.listsView.hide()
        binding.messageView.hide()
        viewModel.load()
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
        ListAdapter<AccountListState, BindingHolder<ItemAddOrRemoveFromListBinding>>(Differ) {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): BindingHolder<ItemAddOrRemoveFromListBinding> {
            val binding =
                ItemAddOrRemoveFromListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return BindingHolder(binding)
        }

        override fun onBindViewHolder(holder: BindingHolder<ItemAddOrRemoveFromListBinding>, position: Int) {
            val item = getItem(position)
            holder.binding.listNameView.text = item.list.title
            holder.binding.addButton.apply {
                visible(!item.includesAccount)
                setOnClickListener {
                    viewModel.addAccountToList(item.list.id)
                }
            }
            holder.binding.removeButton.apply {
                visible(item.includesAccount)
                setOnClickListener {
                    viewModel.removeAccountFromList(item.list.id)
                }
            }
        }
    }

    companion object {
        private const val ARG_ACCOUNT_ID = "accountId"

        fun newInstance(accountId: String): ListsForAccountFragment {
            val args = Bundle().apply {
                putString(ARG_ACCOUNT_ID, accountId)
            }
            return ListsForAccountFragment().apply { arguments = args }
        }
    }
}
