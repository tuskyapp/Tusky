@file:JvmName("MuteAccountDialog")

package com.keylesspalace.tusky.view

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.DialogMuteAccountBinding

fun showMuteAccountDialog(
    activity: Activity,
    accountUsername: String,
    onOk: (notifications: Boolean, duration: Int?) -> Unit
) {
    val binding = DialogMuteAccountBinding.inflate(activity.layoutInflater)
    binding.warning.text = activity.getString(R.string.dialog_mute_warning, accountUsername)
    binding.checkbox.isChecked = true

    val durationLabels = activity.resources.getStringArray(R.array.mute_duration_names)
    binding.durationDropDown.setSimpleItems(durationLabels)

    var selectedDurationIndex = 0
    binding.durationDropDown.setOnItemClickListener { _, _, position, _ ->
        selectedDurationIndex = position
    }
    binding.durationDropDown.setText(durationLabels[selectedDurationIndex], false)

    MaterialAlertDialogBuilder(activity)
        .setView(binding.root)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            val durationValues = activity.resources.getIntArray(R.array.mute_duration_values)

            // workaround to make indefinite muting work with Mastodon 3.3.0
            // https://github.com/tuskyapp/Tusky/issues/2107
            val duration = if (selectedDurationIndex == 0) {
                null
            } else {
                durationValues[selectedDurationIndex]
            }

            onOk(binding.checkbox.isChecked, duration)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}
