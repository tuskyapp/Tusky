package com.keylesspalace.tusky.components.followedtags

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemFollowedHashtagBinding
import com.keylesspalace.tusky.interfaces.HashtagActionListener
import com.keylesspalace.tusky.util.BindingHolder

class FollowedTagsAdapter(
    private val actionListener: HashtagActionListener,
    private val viewModel: FollowedTagsViewModel,
) : PagingDataAdapter<String, BindingHolder<ItemFollowedHashtagBinding>>(STRING_COMPARATOR) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemFollowedHashtagBinding> =
        BindingHolder(ItemFollowedHashtagBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: BindingHolder<ItemFollowedHashtagBinding>, position: Int) {
        viewModel.tags[position].let { tag ->
            holder.itemView.findViewById<TextView>(R.id.followed_tag).text = tag.name
            holder.itemView.findViewById<ImageButton>(R.id.followed_tag_unfollow).setOnClickListener {
                actionListener.unfollow(tag.name, holder.bindingAdapterPosition)
            }
        }
    }

    override fun getItemCount(): Int = viewModel.tags.size

    companion object {
        val STRING_COMPARATOR = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        }
    }
}
