package com.keylesspalace.tusky.components.filters

import android.content.Context
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemRemovableBinding
import com.keylesspalace.tusky.entity.Filter

class FiltersAdapter(context: Context, val listener: FiltersListener, val filters: List<Filter>):
    ArrayAdapter<String>(context, R.layout.item_removable, filters.map{ filter ->
        if (filter.expiresAt == null) {
            filter.title
        } else {
            context.getString(
                R.string.filter_expiration_format,
                filter.title,
                DateUtils.getRelativeTimeSpanString(
                    filter.expiresAt.time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                )
            )
        }
    }) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = if (convertView == null) {
            ItemRemovableBinding.inflate(LayoutInflater.from(context), parent, false)
        } else {
            ItemRemovableBinding.bind(convertView)
        }
        binding.text.text = filters[position].title

        binding.delete.setOnClickListener {
            deleteFilter(position)
        }

        binding.root.setOnClickListener {
            editFilter(position)
        }

        return binding.root
    }

    private fun deleteFilter(position: Int) {
        listener.deleteFilter(filters[position])
    }

    private fun editFilter(position: Int) {
        listener.updateFilter(filters[position])
    }
}