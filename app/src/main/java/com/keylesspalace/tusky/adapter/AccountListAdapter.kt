package com.keylesspalace.tusky.adapter

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.db.AccountEntity
import com.pkmmte.view.CircularImageView
import com.squareup.picasso.Picasso

class AccountListAdapter(private val accountList: List<AccountEntity>, private val onAccountSelectedListener : OnAccountSelectedListener) : RecyclerView.Adapter<AccountListAdapter.AccountListViewHolder>() {

    override fun getItemCount(): Int {
        return accountList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountListViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_account, parent, false) as RelativeLayout
        return AccountListViewHolder(view)
    }

    override fun onBindViewHolder(holder: AccountListViewHolder, position: Int) {
        val account = accountList[position]

        val avatar = holder.accountItem.findViewById<CircularImageView>(R.id.account_avatar)
        Picasso.with(holder.accountItem.context).load(account.profilePictureUrl)
                .error(R.drawable.avatar_default)
                .placeholder(R.drawable.avatar_default)
                .into(avatar)

        val displayName = holder.accountItem.findViewById<TextView>(R.id.account_display_name)
        displayName.text = account.displayName

        val username = holder.accountItem.findViewById<TextView>(R.id.account_username)
        username.text = account.username

        holder.accountItem.setOnClickListener {
            onAccountSelectedListener.onAccountSelected(account)
        }
    }

    class AccountListViewHolder(val accountItem: RelativeLayout) : RecyclerView.ViewHolder(accountItem)

}

interface OnAccountSelectedListener {
    fun onAccountSelected(account: AccountEntity)
}
