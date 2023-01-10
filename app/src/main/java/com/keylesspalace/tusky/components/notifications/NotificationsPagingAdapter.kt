package com.keylesspalace.tusky.components.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.util.parseAsMastodonHtml

class NotificationsPagingAdapter(diffCallback: DiffUtil.ItemCallback<Notification>) :
    PagingDataAdapter<Notification, RecyclerView.ViewHolder>(diffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        val view = inflater.inflate(android.R.layout.simple_list_item_1, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        bindViewHolder(holder, position, null)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        bindViewHolder(holder, position, payloads)
    }

    private fun bindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<*>?
    ) {
        getItem(position)?.let { (holder as NotificationViewHolder).bind(it) }
    }

    private class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text: TextView

        init {
            text = itemView.findViewById(android.R.id.text1)
        }

        fun bind(notification: Notification) {
            text.text = notification.status?.content?.parseAsMastodonHtml()
        }
    }
}
