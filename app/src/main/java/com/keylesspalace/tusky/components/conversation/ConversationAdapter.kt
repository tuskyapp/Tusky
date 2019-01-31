package com.keylesspalace.tusky.components.conversation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.NetworkStateViewHolder
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.NetworkState

class ConversationAdapter(private val useAbsoluteTime: Boolean,
                          private val mediaPreviewEnabled: Boolean,
                          private val listener: StatusActionListener,
                          private val retryCallback: () -> Unit)
 : PagedListAdapter<ConversationEntity, RecyclerView.ViewHolder>(CONVERSATION_COMPARATOR) {

    private var networkState: NetworkState? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return when (viewType) {
            R.layout.item_network_state -> NetworkStateViewHolder(view, retryCallback)
            R.layout.item_conversation -> ConversationViewHolder(view, listener, useAbsoluteTime, mediaPreviewEnabled)
            else -> throw IllegalArgumentException("unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            R.layout.item_network_state -> (holder as NetworkStateViewHolder).setUpWithNetworkState(networkState)
            R.layout.item_conversation -> (holder as ConversationViewHolder).setupWithConversation(getItem(position))
        }
    }

    private fun hasExtraRow() = networkState != null && networkState != NetworkState.LOADED

    override fun getItemViewType(position: Int): Int {
        return if (hasExtraRow() && position == itemCount - 1) {
            R.layout.item_network_state
        } else {
            R.layout.item_conversation
        }
    }

    override fun getItemCount(): Int {
        return super.getItemCount() + if (hasExtraRow()) 1 else 0
    }

    fun setNetworkState(newNetworkState: NetworkState?) {
        val previousState = this.networkState
        val hadExtraRow = hasExtraRow()
        this.networkState = newNetworkState
        val hasExtraRow = hasExtraRow()
        if (hadExtraRow != hasExtraRow) {
            if (hadExtraRow) {
                notifyItemRemoved(super.getItemCount())
            } else {
                notifyItemInserted(super.getItemCount())
            }
        } else if (hasExtraRow && previousState != newNetworkState) {
            notifyItemChanged(itemCount - 1)
        }
    }


    companion object {

        val CONVERSATION_COMPARATOR = object : DiffUtil.ItemCallback<ConversationEntity>() {
            override fun areContentsTheSame(oldItem: ConversationEntity, newItem: ConversationEntity): Boolean =
                    oldItem == newItem

            override fun areItemsTheSame(oldItem: ConversationEntity, newItem: ConversationEntity): Boolean =
                    oldItem.id == newItem.id
        }

    }

}