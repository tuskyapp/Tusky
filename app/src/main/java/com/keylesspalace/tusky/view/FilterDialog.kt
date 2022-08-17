package com.keylesspalace.tusky.view

import android.content.Context
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import com.keylesspalace.tusky.FiltersActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.DialogFilterBinding
import com.keylesspalace.tusky.entity.Filter
import java.util.Date

fun showAddFilterDialog(activity: FiltersActivity) {
    val binding = DialogFilterBinding.inflate(activity.layoutInflater)
    binding.phraseWholeWord.isChecked = true
    binding.filterDurationSpinner.adapter = ArrayAdapter(
        activity,
        android.R.layout.simple_list_item_1,
        activity.resources.getStringArray(R.array.filter_duration_names)
    )
    AlertDialog.Builder(activity)
        .setTitle(R.string.filter_addition_dialog_title)
        .setView(binding.root)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            activity.createFilter(
                binding.phraseEditText.text.toString(), binding.phraseWholeWord.isChecked,
                getSecondsForDurationIndex(binding.filterDurationSpinner.selectedItemPosition, activity)
            )
        }
        .setNeutralButton(android.R.string.cancel, null)
        .show()
}

fun setupEditDialogForFilter(activity: FiltersActivity, filter: Filter, itemIndex: Int) {
    val binding = DialogFilterBinding.inflate(activity.layoutInflater)
    binding.phraseEditText.setText(filter.phrase)
    binding.phraseWholeWord.isChecked = filter.wholeWord
    val filterNames = activity.resources.getStringArray(R.array.filter_duration_names).toMutableList()
    if (filter.expiresAt != null) {
        filterNames.add(0, activity.getString(R.string.duration_no_change))
    }
    binding.filterDurationSpinner.adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, filterNames)

    AlertDialog.Builder(activity)
        .setTitle(R.string.filter_edit_dialog_title)
        .setView(binding.root)
        .setPositiveButton(R.string.filter_dialog_update_button) { _, _ ->
            var index = binding.filterDurationSpinner.selectedItemPosition
            if (filter.expiresAt != null) {
                // We prepended "No changes", account for that here
                --index
            }
            activity.updateFilter(
                filter.id, binding.phraseEditText.text.toString(), filter.context,
                filter.irreversible, binding.phraseWholeWord.isChecked,
                getSecondsForDurationIndex(index, activity, filter.expiresAt), itemIndex
            )
        }
        .setNegativeButton(R.string.filter_dialog_remove_button) { _, _ ->
            activity.deleteFilter(itemIndex)
        }
        .setNeutralButton(android.R.string.cancel, null)
        .show()
}

// Mastodon *stores* the absolute date in the filter,
// but create/edit take a number of seconds (relative to the time the operation is posted)
fun getSecondsForDurationIndex(index: Int, context: Context?, default: Date? = null): Int? {
    return when (index) {
        -1 -> if (default == null) { default } else { ((default.time - System.currentTimeMillis()) / 1000).toInt() }
        0 -> null
        else -> context?.resources?.getIntArray(R.array.filter_duration_values)?.get(index)
    }
}
