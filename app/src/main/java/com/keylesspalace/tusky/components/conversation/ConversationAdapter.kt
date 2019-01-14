package com.keylesspalace.tusky.components.conversation

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.entity.Conversation
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import com.keylesspalace.tusky.R
import kotlinx.android.synthetic.main.item_conversation.view.*

class ConversationAdapter(val likeListener: (ConversationEntity) -> Unit): PagedListAdapter<ConversationEntity, RecyclerView.ViewHolder>(POST_COMPARATOR) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        holder.itemView.status_username.text = getItem(position)?.lastStatus?.content


        if(getItem(position)?.lastStatus?.favourited == true) {
            holder.itemView.setBackgroundColor(Color.RED)
        } else {
            holder.itemView.setBackgroundColor(Color.BLUE)
        }
        holder.itemView.setOnClickListener{
            likeListener(getItem(position)!!)
        }

    }


    companion object {
        private val PAYLOAD_SCORE = Any()
        val POST_COMPARATOR = object : DiffUtil.ItemCallback<ConversationEntity>() {
            override fun areContentsTheSame(oldItem: ConversationEntity, newItem: ConversationEntity): Boolean =
                    oldItem == newItem

            override fun areItemsTheSame(oldItem: ConversationEntity, newItem: ConversationEntity): Boolean =
                    oldItem.id == newItem.id

        }

    }

    class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView)

}