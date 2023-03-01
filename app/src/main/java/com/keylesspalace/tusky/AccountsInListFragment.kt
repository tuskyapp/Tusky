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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import com.keylesspalace.tusky.databinding.FragmentAccountsInListBinding
import com.keylesspalace.tusky.databinding.ItemFollowRequestBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.BindingHolder
import com.keylesspalace.tusky.util.Either
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.loadAvatar
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.unsafeLazy
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.viewmodel.AccountsInListViewModel
import com.keylesspalace.tusky.viewmodel.State
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

private typealias AccountInfo = Pair<TimelineAccount, Boolean>

class AccountsInListFragment : DialogFragment(), Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: AccountsInListViewModel by viewModels { viewModelFactory }
    private val binding by viewBinding(FragmentAccountsInListBinding::bind)

    private lateinit var listId: String
    private lateinit var listName: String
    private val adapter = Adapter()
    private val searchAdapter = SearchAdapter()

    private val radius by unsafeLazy { resources.getDimensionPixelSize(R.dimen.avatar_radius_48dp) }
    private val pm by unsafeLazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
    private val animateAvatar by unsafeLazy { pm.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false) }
    private val animateEmojis by unsafeLazy { pm.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.TuskyDialogFragmentStyle)
        val args = requireArguments()
        listId = args.getString(LIST_ID_ARG)!!
        listName = args.getString(LIST_NAME_ARG)!!

        viewModel.load(listId)
    }

    override fun onStart() {
        super.onStart()
        dialog?.apply {
            // Stretch dialog to the window
            window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_accounts_in_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.accountsRecycler.layoutManager = LinearLayoutManager(view.context)
        binding.accountsRecycler.adapter = adapter

        binding.accountsSearchRecycler.layoutManager = LinearLayoutManager(view.context)
        binding.accountsSearchRecycler.adapter = searchAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { state ->
                adapter.submitList(state.accounts.asRightOrNull() ?: listOf())

                when (state.accounts) {
                    is Either.Right -> binding.messageView.hide()
                    is Either.Left -> handleError(state.accounts.value)
                }

                setupSearchView(state)
            }
        }

        binding.searchView.isSubmitButtonEnabled = true
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.search(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Close event is not sent so we use this instead
                if (newText.isNullOrBlank()) {
                    viewModel.search("")
                }
                return true
            }
        })
    }

    private fun setupSearchView(state: State) {
        if (state.searchResult == null) {
            searchAdapter.submitList(listOf())
            binding.accountsSearchRecycler.hide()
            binding.accountsRecycler.show()
        } else {
            val listAccounts = state.accounts.asRightOrNull() ?: listOf()
            val newList = state.searchResult.map { acc ->
                acc to listAccounts.contains(acc)
            }
            searchAdapter.submitList(newList)
            binding.accountsSearchRecycler.show()
            binding.accountsRecycler.hide()
        }
    }

    private fun handleError(error: Throwable) {
        binding.messageView.show()
        val retryAction = { _: View ->
            binding.messageView.hide()
            viewModel.load(listId)
        }
        if (error is IOException) {
            binding.messageView.setup(
                R.drawable.elephant_offline,
                R.string.error_network, retryAction
            )
        } else {
            binding.messageView.setup(
                R.drawable.elephant_error,
                R.string.error_generic, retryAction
            )
        }
    }

    private fun onRemoveFromList(accountId: String) {
        viewModel.deleteAccountFromList(listId, accountId)
    }

    private fun onAddToList(account: TimelineAccount) {
        viewModel.addAccountToList(listId, account)
    }

    private object AccountDiffer : DiffUtil.ItemCallback<TimelineAccount>() {
        override fun areItemsTheSame(oldItem: TimelineAccount, newItem: TimelineAccount): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TimelineAccount, newItem: TimelineAccount): Boolean {
            return oldItem == newItem
        }
    }

    inner class Adapter : ListAdapter<TimelineAccount, BindingHolder<ItemFollowRequestBinding>>(AccountDiffer) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemFollowRequestBinding> {
            val binding = ItemFollowRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            val holder = BindingHolder(binding)

            binding.notificationTextView.hide()
            binding.acceptButton.hide()
            binding.rejectButton.setOnClickListener {
                onRemoveFromList(getItem(holder.bindingAdapterPosition).id)
            }
            binding.rejectButton.contentDescription =
                binding.root.context.getString(R.string.action_remove_from_list)

            return holder
        }

        override fun onBindViewHolder(holder: BindingHolder<ItemFollowRequestBinding>, position: Int) {
            val account = getItem(position)
            holder.binding.displayNameTextView.text = account.name.emojify(account.emojis, holder.binding.displayNameTextView, animateEmojis)
            holder.binding.usernameTextView.text = account.username
            loadAvatar(account.avatar, holder.binding.avatar, radius, animateAvatar)
        }
    }

    private object SearchDiffer : DiffUtil.ItemCallback<AccountInfo>() {
        override fun areItemsTheSame(oldItem: AccountInfo, newItem: AccountInfo): Boolean {
            return oldItem.first.id == newItem.first.id
        }

        override fun areContentsTheSame(oldItem: AccountInfo, newItem: AccountInfo): Boolean {
            return oldItem == newItem
        }
    }

    inner class SearchAdapter : ListAdapter<AccountInfo, BindingHolder<ItemFollowRequestBinding>>(SearchDiffer) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemFollowRequestBinding> {
            val binding = ItemFollowRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            val holder = BindingHolder(binding)

            binding.notificationTextView.hide()
            binding.acceptButton.hide()
            binding.rejectButton.setOnClickListener {
                val (account, inAList) = getItem(holder.bindingAdapterPosition)
                if (inAList) {
                    onRemoveFromList(account.id)
                } else {
                    onAddToList(account)
                }
            }

            return holder
        }

        override fun onBindViewHolder(holder: BindingHolder<ItemFollowRequestBinding>, position: Int) {
            val (account, inAList) = getItem(position)

            holder.binding.displayNameTextView.text = account.name.emojify(account.emojis, holder.binding.displayNameTextView, animateEmojis)
            holder.binding.usernameTextView.text = account.username
            loadAvatar(account.avatar, holder.binding.avatar, radius, animateAvatar)

            holder.binding.rejectButton.apply {
                contentDescription = if (inAList) {
                    setImageResource(R.drawable.ic_reject_24dp)
                    getString(R.string.action_remove_from_list)
                } else {
                    setImageResource(R.drawable.ic_plus_24dp)
                    getString(R.string.action_add_to_list)
                }
            }
        }
    }

    companion object {
        private const val LIST_ID_ARG = "listId"
        private const val LIST_NAME_ARG = "listName"

        @JvmStatic
        fun newInstance(listId: String, listName: String): AccountsInListFragment {
            val args = Bundle().apply {
                putString(LIST_ID_ARG, listId)
                putString(LIST_NAME_ARG, listName)
            }
            return AccountsInListFragment().apply { arguments = args }
        }
    }
}
