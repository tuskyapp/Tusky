package com.keylesspalace.tusky.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemFollowedHashtagBinding
import com.keylesspalace.tusky.interfaces.HashtagActionListener

class FollowedTagsAdapter(
    inputContext: Context,
    private val actionListener: HashtagActionListener,
    tags: List<String>,
) : ArrayAdapter<String>(inputContext, R.layout.item_followed_hashtag, tags) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = if (convertView == null) {
            ItemFollowedHashtagBinding.inflate(LayoutInflater.from(context), parent, false)
        } else {
            ItemFollowedHashtagBinding.bind(convertView)
        }

        getItem(position)?.let { tag ->
            binding.followedTag.text = tag
            binding.followedTagUnfollow.setOnClickListener {
                actionListener.unfollow(tag, position)
            }
        }

        return binding.root
    }
}
