package com.keylesspalace.tusky.util

import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

class BindingViewHolder<T : ViewBinding>(
        val binding: T
) : RecyclerView.ViewHolder(binding.root)
