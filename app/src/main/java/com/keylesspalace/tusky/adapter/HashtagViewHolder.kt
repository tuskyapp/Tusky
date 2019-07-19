package com.keylesspalace.tusky.adapter

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.interfaces.LinkListener

class HashtagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val hashtag: TextView = itemView.findViewById(R.id.hashtag)

    fun setup(tag: String, listener: LinkListener) {
        hashtag.text = String.format("#%s", tag)
        hashtag.setOnClickListener { listener.onViewTag(tag) }
    }
}