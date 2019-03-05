package com.keylesspalace.tusky

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.viewmodel.AccountsInListViewModel
import com.squareup.picasso.Picasso
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from
import com.uber.autodispose.autoDisposable
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.fragment_accounts_in_list.*
import javax.inject.Inject

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

    lateinit var listId: String
    lateinit var listName: String
    val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = viewModelFactory.create(AccountsInListViewModel::class.java)
        val args = arguments
                ?: throw IllegalStateException("No arguments specified for AccountsInListFragment")
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

        viewModel.state
                .observeOn(AndroidSchedulers.mainThread())
                .autoDisposable(from(this))
                .subscribe { state ->
                    adapter.accounts.clear()
                    adapter.accounts.addAll(state.accounts)
                    adapter.notifyDataSetChanged()
                }

        searchNewAccountEditText.setOnEditorActionListener { _, _, _ ->
            viewModel.load(listId)
            true
        }
    }

    class Adapter : RecyclerView.Adapter<Adapter.ViewHolder>() {
        val accounts = mutableListOf<Account>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_follow_request, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int = accounts.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val account = accounts[position]
            holder.username.text = account.username
            holder.displayName.text = account.displayName
            Picasso.with(holder.avatar.context)
                    .load(account.avatar)
                    .fit()
                    .placeholder(R.drawable.avatar_default)
                    .into(holder.avatar)
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val avatar: ImageView = itemView.findViewById(R.id.follow_request_avatar)
            val displayName: TextView = itemView.findViewById(R.id.follow_request_display_name)
            val username: TextView = itemView.findViewById(R.id.follow_request_username)
            val rejectButton: ImageButton = itemView.findViewById(R.id.follow_request_reject)

            init {
                itemView.findViewById<View>(R.id.follow_request_accept).hide()
            }
        }
    }
}