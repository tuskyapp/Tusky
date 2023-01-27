package com.keylesspalace.tusky.components.filters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemRemovableBinding
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.util.getRelativeTimeSpanString

class FiltersAdapter(context: Context, val listener: FiltersListener, filters: List<Filter>) :
    ArrayAdapter<Filter>(context, R.layout.item_removable, filters) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = if (convertView == null) {
            ItemRemovableBinding.inflate(LayoutInflater.from(context), parent, false)
        } else {
            ItemRemovableBinding.bind(convertView)
        }

        val resources = binding.root.resources
        val actions = resources.getStringArray(R.array.filter_actions)
        val contexts = resources.getStringArray(R.array.filter_contexts)

        getItem(position)?.let { filter ->
            val context = binding.root.context
            binding.textPrimary.text = if (filter.expiresAt == null) {
                filter.title
            } else {
                context.getString(
                    R.string.filter_expiration_format,
                    filter.title,
                    getRelativeTimeSpanString(binding.root.context, filter.expiresAt.time, System.currentTimeMillis())
                )
            }
            binding.textSecondary.text = context.getString(
                R.string.filter_description_format,
                actions.getOrNull(filter.action.ordinal - 1),
                filter.context.map { contexts.getOrNull(Filter.Kind.from(it).ordinal) }.joinToString("/")
            )
        }

        binding.delete.setOnClickListener {
            getItem(position)?.let { listener.deleteFilter(it) }
        }

        binding.root.setOnClickListener {
            getItem(position)?.let { listener.updateFilter(it) }
        }

        return binding.root
    }
}
