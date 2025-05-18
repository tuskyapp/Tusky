/* Copyright 2019 Tusky Contributors
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

@file:JvmName("AddPollDialog")

package com.keylesspalace.tusky.components.compose.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.DialogAddPollBinding
import com.keylesspalace.tusky.entity.NewPoll

fun showAddPollDialog(
    context: Context,
    poll: NewPoll?,
    maxOptionCount: Int,
    maxOptionLength: Int,
    minDuration: Int,
    maxDuration: Int,
    onUpdatePoll: (NewPoll) -> Unit
) {
    val binding = DialogAddPollBinding.inflate(LayoutInflater.from(context))

    val inset = context.resources.getDimensionPixelSize(R.dimen.dialog_inset)

    val dialog = MaterialAlertDialogBuilder(context)
        .setIcon(R.drawable.ic_insert_chart_24dp_filled)
        .setTitle(R.string.create_poll_title)
        .setView(binding.root)
        .setBackgroundInsetTop(inset)
        .setBackgroundInsetEnd(inset)
        .setBackgroundInsetBottom(inset)
        .setBackgroundInsetStart(inset)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(android.R.string.ok, null)
        .setCancelable(false)
        .create()

    val adapter = AddPollOptionsAdapter(
        options = poll?.options?.toMutableList() ?: mutableListOf("", ""),
        maxOptionLength = maxOptionLength,
        onOptionRemoved = { valid ->
            binding.addChoiceButton.isEnabled = true
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = valid
        },
        onOptionChanged = { valid ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = valid
        }
    )

    binding.pollChoices.adapter = adapter

    var durations = context.resources.getIntArray(R.array.poll_duration_values).toList()
    val durationLabels = context.resources.getStringArray(
        R.array.poll_duration_names
    ).filterIndexed { index, _ -> durations[index] in minDuration..maxDuration }

    binding.pollDurationDropDown.setSimpleItems(durationLabels.toTypedArray())
    durations = durations.filter { it in minDuration..maxDuration }

    binding.addChoiceButton.setOnClickListener {
        if (adapter.itemCount < maxOptionCount) {
            adapter.addChoice()
        }
        if (adapter.itemCount >= maxOptionCount) {
            it.isEnabled = false
        }
    }

    val secondsInADay = 60 * 60 * 24
    val desiredDuration = poll?.expiresIn ?: secondsInADay
    var selectedDurationIndex = durations.indexOfLast {
        it <= desiredDuration
    }

    binding.pollDurationDropDown.setText(durationLabels[selectedDurationIndex], false)
    binding.pollDurationDropDown.setOnItemClickListener { _, _, position, _ ->
        selectedDurationIndex = position
    }

    binding.multipleChoicesCheckBox.isChecked = poll?.multiple == true

    fun newPoll() = NewPoll(
        options = adapter.pollOptions,
        expiresIn = durations[selectedDurationIndex],
        multiple = binding.multipleChoicesCheckBox.isChecked
    )

    val existingPoll = newPoll()

    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            onUpdatePoll(
                newPoll()
            )

            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
            if (existingPoll != newPoll()) {
                MaterialAlertDialogBuilder(context)
                    .setMessage(R.string.confirm_dismiss_caption)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.no, null)
                    .show()
            } else {
                dialog.dismiss()
            }
        }
    }

    dialog.show()

    // yes, SOFT_INPUT_ADJUST_RESIZE is deprecated, but without it the dropdown can get behind the keyboard
    dialog.window?.setSoftInputMode(
        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    )

    binding.pollChoices.post {
        val firstItemView = binding.pollChoices.layoutManager?.findViewByPosition(0)
        val editText = firstItemView?.findViewById<TextInputEditText>(R.id.optionEditText)
        editText?.requestFocus()
        editText?.setSelection(editText.length())
    }

    // make the dialog focusable so the keyboard does not stay behind it
    dialog.window?.clearFlags(
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
    )
}
