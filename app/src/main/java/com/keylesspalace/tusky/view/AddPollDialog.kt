@file:JvmName("AddPollDialog")

package com.keylesspalace.tusky.view

import androidx.appcompat.app.AlertDialog
import com.keylesspalace.tusky.ComposeActivity
import com.keylesspalace.tusky.adapter.AddPollOptionsAdapter
import com.keylesspalace.tusky.entity.NewPoll
import kotlinx.android.synthetic.main.dialog_add_poll.view.*
import android.view.WindowManager
import com.keylesspalace.tusky.R


private const val DEFAULT_MAX_OPTION_COUNT = 4
private const val DEFAULT_MAX_OPTION_LENGTH = 25


fun showAddPollDialog(
        activity: ComposeActivity,
        poll: NewPoll?,
        maxOptionCount: Int?,
        maxOptionLength: Int?
) {

    val view = activity.layoutInflater.inflate(R.layout.dialog_add_poll, null)

    val adapter = AddPollOptionsAdapter(
            options = poll?.options?.toMutableList() ?: mutableListOf("", ""),
            maxOptionLength = maxOptionLength ?: DEFAULT_MAX_OPTION_LENGTH
    )
    view.pollChoices.adapter = adapter

    view.addChoiceButton.setOnClickListener {
        if (adapter.itemCount < maxOptionCount ?: DEFAULT_MAX_OPTION_COUNT) {
            adapter.addChoice()
        }
    }

    val pollDurationId = activity.resources.getIntArray(R.array.poll_duration_values).indexOfLast {
        it <= poll?.expires_in ?: 0
    }

    view.pollDurationSpinner.setSelection(pollDurationId)

    val dialog = AlertDialog.Builder(activity)
            .setIcon(R.drawable.ic_poll_24dp)
            .setTitle(R.string.create_poll_title)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->

                val selectedPollDurationId = view.pollDurationSpinner.selectedItemPosition

                val pollDuration = activity.resources.getIntArray(R.array.poll_duration_values)[selectedPollDurationId]

                activity.updatePoll(
                        NewPoll(
                                options = adapter.pollOptions,
                                expires_in = pollDuration,
                                multiple = view.multipleChoicesCheckBox.isChecked
                        )
                )
            }
            .show()

    dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)

}