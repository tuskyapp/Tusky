package com.keylesspalace.tusky.components.filters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemRemovableBinding
import com.keylesspalace.tusky.entity.FilterKeyword

class KeywordsAdapter(context: Context, val listener: KeywordsListener, keywords: List<FilterKeyword>):
    ArrayAdapter<FilterKeyword>(context, R.layout.item_removable, keywords) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = if (convertView == null) {
            ItemRemovableBinding.inflate(LayoutInflater.from(context), parent, false)
        } else {
            ItemRemovableBinding.bind(convertView)
        }

        getItem(position)?.let { keyword ->
            val label = if (keyword.wholeWord) {
                binding.root.context.getString(R.string.filter_keyword_display_format, keyword.keyword)
            } else {
                keyword.keyword
            }
            binding.text.text = label
            binding.delete.setOnClickListener {
                listener.deleteKeyword(keyword)
            }
            binding.root.setOnClickListener {
                listener.showEditKeywordUI(keyword)
            }
        }

        return binding.root
    }
}