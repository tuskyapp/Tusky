package com.keylesspalace.tusky

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.util.Either
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.viewmodel.AccountsInListViewModel
import com.keylesspalace.tusky.viewmodel.State
import com.squareup.picasso.Picasso
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from
import com.uber.autodispose.autoDisposable
import io.reactivex.android.schedulers.AndroidSchedulers
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.TuskyDialogFragmentStyle)
        viewModel = viewModelFactory.create(AccountsInListViewModel::class.java)
        val args = arguments!!
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
                .autoDisposable(from(this))
                .subscribe { state ->
                    adapter.accounts.clear()
                    state.accounts.map(adapter.accounts::addAll)
                    adapter.notifyDataSetChanged()

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
            searchAdapter.accounts.clear()
            accountsSearchRecycler.hide()
        } else {
            searchAdapter.accounts.clear()
            val listAccounts = state.accounts.asRightOrNull() ?: listOf()
            searchAdapter.accounts.addAll(state.searchResult.map { acc ->
                acc to listAccounts.contains(acc)
            })
            searchAdapter.notifyDataSetChanged()
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
        val accounts = mutableListOf<Account>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_follow_request, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int = accounts.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(accounts[position])
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
                View.OnClickListener {

            init {
                acceptButton.hide()
                rejectButton.setOnClickListener(this)
                rejectButton.contentDescription =
                        itemView.context.getString(R.string.action_remove_from_list)
            }

            fun bind(account: Account) {
                usernameTextView.text = account.username
                displayNameTextView.text = account.displayName
                Picasso.with(avatar.context)
                        .load(account.avatar)
                        .fit()
                        .placeholder(R.drawable.avatar_default)
                        .into(avatar)
            }

            override fun onClick(v: View?) {
                onRemoveFromList(accounts[adapterPosition].id)
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
        val accounts = mutableListOf<AccountInfo>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_follow_request, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int = accounts.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (account, inAList) = accounts[position]
            holder.bind(account, inAList)

        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
                View.OnClickListener {

            fun bind(account: Account, inAList: Boolean) {
                usernameTextView.text = account.username
                displayNameTextView.text = account.displayName
                Picasso.with(avatar.context)
                        .load(account.avatar)
                        .fit()
                        .placeholder(R.drawable.avatar_default)
                        .into(avatar)
                rejectButton.apply {
                    if (inAList) {
                        setImageResource(R.drawable.ic_reject_24dp)
                        contentDescription = getString(R.string.action_remove_from_list)
                    } else {
                        setImageResource(R.drawable.ic_plus_24dp)
                        contentDescription = getString(R.string.action_add_to_list)
                    }
                }
            }

            init {
                acceptButton.hide()
                rejectButton.setOnClickListener(this)
            }

            override fun onClick(v: View?) {
                val (account, inAList) = accounts[adapterPosition]
                if (inAList) {
                    onRemoveFromList(account.id)
                } else {
                    onAddToList(account)
                }
            }
        }
    }
}