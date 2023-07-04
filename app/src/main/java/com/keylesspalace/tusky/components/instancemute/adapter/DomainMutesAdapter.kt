package com.keylesspalace.tusky.components.instancemute.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import com.keylesspalace.tusky.components.followedtags.FollowedTagsAdapter.Companion.STRING_COMPARATOR
import com.keylesspalace.tusky.databinding.ItemMutedDomainBinding
import com.keylesspalace.tusky.util.BindingHolder

class DomainMutesAdapter(
    private val onUnmute: (String) -> Unit
) : PagingDataAdapter<String, BindingHolder<ItemMutedDomainBinding>>(STRING_COMPARATOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemMutedDomainBinding> {
        val binding = ItemMutedDomainBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemMutedDomainBinding>, position: Int) {
        getItem(position)?.let { instance ->
            holder.binding.mutedDomain.text = instance
            holder.binding.mutedDomainUnmute.setOnClickListener {
                onUnmute(instance)
            }
        }
    }
}
