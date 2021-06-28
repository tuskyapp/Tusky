@file:JvmName("MuteAccountDialog")

package com.keylesspalace.tusky.view

import android.app.Activity
import androidx.appcompat.app.AlertDialog
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

    AlertDialog.Builder(activity)
        .setView(binding.root)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            val durationValues = activity.resources.getIntArray(R.array.mute_duration_values)

            // workaround to make indefinite muting work with Mastodon 3.3.0
            // https://github.com/tuskyapp/Tusky/issues/2107
            val duration = if (binding.duration.selectedItemPosition == 0) {
                null
            } else {
                durationValues[binding.duration.selectedItemPosition]
            }

            onOk(binding.checkbox.isChecked, duration)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}
