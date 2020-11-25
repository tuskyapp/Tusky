@file:JvmName("MuteAccountDialog")

package com.keylesspalace.tusky.view

import android.app.Activity
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.keylesspalace.tusky.R

fun showMuteAccountDialog(
    activity: Activity,
    accountUsername: String,
    onOk: (notifications: Boolean) -> Unit
) {
    val view = activity.layoutInflater.inflate(R.layout.dialog_mute_account, null)
    (view.findViewById(R.id.warning) as TextView).text =
        activity.getString(R.string.dialog_mute_warning, accountUsername)
    val checkbox: CheckBox = view.findViewById(R.id.checkbox)
    checkbox.isChecked = true

    AlertDialog.Builder(activity)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ -> onOk(checkbox.isChecked) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
}