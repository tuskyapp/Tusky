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
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.util.*
import com.keylesspalace.tusky.viewmodel.AccountsInListViewModel
import com.keylesspalace.tusky.viewmodel.State
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from
import com.uber.autodispose.autoDispose
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.fragment_accounts_in_list.*
import kotlinx.android.synthetic.main.item_follow_request.*
import java.io.IOException
import javax.inject.Inject

private typealias AccountInfo = Pair<Account, Boolean>

class AccountsInListFragment : DialogFragment(), Injectable {

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

    @Inject
    lateinit var viewModelFactory: ViewModelFactory
    lateinit var viewModel: AccountsInListViewModel

    private lateinit var listId: String
    private lateinit var listName: String
    private val adapter = Adapter()
    private val searchAdapter = SearchAdapter()

    private val radius by lazy { resources.getDimensionPixelSize(R.dimen.avatar_radius_48dp) }
    private val animateAvatar by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean("animateGifAvatars", false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.TuskyDialogFragmentStyle)
        viewModel = viewModelFactory.create(AccountsInListViewModel::class.java)
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
        super.onViewCreated(view, savedInstanceState)
        accountsRecycler.layoutManager = LinearLayoutManager(view.context)
        accountsRecycler.adapter = adapter

        accountsSearchRecycler.layoutManager = LinearLayoutManager(view.context)
        accountsSearchRecycler.adapter = searchAdapter

        viewModel.state
                .observeOn(AndroidSchedulers.mainThread())
                .autoDispose(from(this))
                .subscribe { state ->
                    adapter.submitList(state.accounts.asRightOrNull() ?: listOf())

                    when (state.accounts) {
                        is Either.Right -> messageView.hide()
                        is Either.Left -> handleError(state.accounts.value)
                    }

                    setupSearchView(state)
                }

        searchView.isSubmitButtonEnabled = true
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.search(query ?: "")
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
            accountsSearchRecycler.hide()
        } else {
            val listAccounts = state.accounts.asRightOrNull() ?: listOf()
            val newList = state.searchResult.map { acc ->
                acc to listAccounts.contains(acc)
            }
            searchAdapter.submitList(newList)
            accountsSearchRecycler.show()
        }
    }

    private fun handleError(error: Throwable) {
        messageView.show()
        val retryAction = { _: View ->
            messageView.hide()
            viewModel.load(listId)
        }
        if (error is IOException) {
            messageView.setup(R.drawable.elephant_offline,
                    R.string.error_network, retryAction)
        } else {
            messageView.setup(R.drawable.elephant_error,
                    R.string.error_generic, retryAction)
        }
    }

    private fun onRemoveFromList(accountId: String) {
        viewModel.deleteAccountFromList(listId, accountId)
    }

    private fun onAddToList(account: Account) {
        viewModel.addAccountToList(listId, account)
    }

    private object AccountDiffer : DiffUtil.ItemCallback<Account>() {
        override fun areItemsTheSame(oldItem: Account, newItem: Account): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Account, newItem: Account): Boolean {
            return oldItem.deepEquals(newItem)
        }
    }

    inner class Adapter : ListAdapter<Account, Adapter.ViewHolder>(AccountDiffer) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_follow_request, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
                View.OnClickListener, LayoutContainer {

            override val containerView = itemView

            init {
                acceptButton.hide()
                rejectButton.setOnClickListener(this)
                rejectButton.contentDescription =
                        itemView.context.getString(R.string.action_remove_from_list)
            }

            fun bind(account: Account) {
                displayNameTextView.text = CustomEmojiHelper.emojifyString(account.name, account.emojis, displayNameTextView)
                usernameTextView.text = account.username
                loadAvatar(account.avatar, avatar, radius, animateAvatar)
            }

            override fun onClick(v: View?) {
                onRemoveFromList(getItem(adapterPosition).id)
            }
        }
    }

    private object SearchDiffer : DiffUtil.ItemCallback<AccountInfo>() {
        override fun areItemsTheSame(oldItem: AccountInfo, newItem: AccountInfo): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: AccountInfo, newItem: AccountInfo): Boolean {
            return oldItem.second == newItem.second
                    && oldItem.first.deepEquals(newItem.first)
        }

    }

    inner class SearchAdapter : ListAdapter<AccountInfo, SearchAdapter.ViewHolder>(SearchDiffer) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_follow_request, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (account, inAList) = getItem(position)
            holder.bind(account, inAList)

        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
                View.OnClickListener, LayoutContainer {

            override val containerView = itemView

            fun bind(account: Account, inAList: Boolean) {
                displayNameTextView.text = CustomEmojiHelper.emojifyString(account.name, account.emojis, displayNameTextView)
                usernameTextView.text = account.username
                loadAvatar(account.avatar, avatar, radius, animateAvatar)

                rejectButton.apply {
                    contentDescription = if (inAList) {
                        setImageResource(R.drawable.ic_reject_24dp)
                        getString(R.string.action_remove_from_list)
                    } else {
                        setImageResource(R.drawable.ic_plus_24dp)
                        getString(R.string.action_add_to_list)
                    }
                }
            }

            init {
                acceptButton.hide()
                rejectButton.setOnClickListener(this)
            }

            override fun onClick(v: View?) {
                val (account, inAList) = getItem(adapterPosition)
                if (inAList) {
                    onRemoveFromList(account.id)
                } else {
                    onAddToList(account)
                }
            }
        }
    }
}