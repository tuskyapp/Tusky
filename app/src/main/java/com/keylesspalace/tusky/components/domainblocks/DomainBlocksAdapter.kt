package com.keylesspalace.tusky.components.domainblocks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import com.keylesspalace.tusky.components.followedtags.FollowedTagsAdapter.Companion.STRING_COMPARATOR
import com.keylesspalace.tusky.databinding.ItemBlockedDomainBinding
import com.keylesspalace.tusky.util.BindingHolder

class DomainBlocksAdapter(
    private val onUnmute: (String) -> Unit
) : PagingDataAdapter<String, BindingHolder<ItemBlockedDomainBinding>>(STRING_COMPARATOR) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BindingHolder<ItemBlockedDomainBinding> {
        val binding = ItemBlockedDomainBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BindingHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemBlockedDomainBinding>, position: Int) {
        getItem(position)?.let { instance ->
            holder.binding.blockedDomain.text = instance
            holder.binding.blockedDomainUnblock.setOnClickListener {
                onUnmute(instance)
            }
        }
    }
}
