package com.keylesspalace.tusky.components.conversation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.AsyncPagedListDiffer
import androidx.paging.PagedList
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.NetworkStateViewHolder
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.NetworkState

class ConversationAdapter(
        private val useAbsoluteTime: Boolean,
        private val mediaPreviewEnabled: Boolean,
        private val listener: StatusActionListener,
        private val topLoadedCallback: () -> Unit,
        private val retryCallback: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var networkState: NetworkState? = null

    private val differ: AsyncPagedListDiffer<ConversationEntity> = AsyncPagedListDiffer(object : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) {
            notifyItemRangeInserted(position, count)
            if (position == 0) {
                topLoadedCallback()
            }
        }

        override fun onRemoved(position: Int, count: Int) {
            notifyItemRangeRemoved(position, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            notifyItemRangeChanged(position, count, payload)
        }
    }, AsyncDifferConfig.Builder(CONVERSATION_COMPARATOR).build())

    fun submitList(list: PagedList<ConversationEntity>) {
        differ.submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return when (viewType) {
            R.layout.item_network_state -> NetworkStateViewHolder(view, retryCallback)
            R.layout.item_conversation -> ConversationViewHolder(view, listener, useAbsoluteTime,
                    mediaPreviewEnabled)
            else -> throw IllegalArgumentException("unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            R.layout.item_network_state -> (holder as NetworkStateViewHolder).setUpWithNetworkState(networkState, differ.itemCount == 0)
            R.layout.item_conversation -> (holder as ConversationViewHolder).setupWithConversation(differ.getItem(position))
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
        return differ.itemCount + if (hasExtraRow()) 1 else 0
    }

    fun setNetworkState(newNetworkState: NetworkState?) {
        val previousState = this.networkState
        val hadExtraRow = hasExtraRow()
        this.networkState = newNetworkState
        val hasExtraRow = hasExtraRow()
        if (hadExtraRow != hasExtraRow) {
            if (hadExtraRow) {
                notifyItemRemoved(differ.itemCount)
            } else {
                notifyItemInserted(differ.itemCount)
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