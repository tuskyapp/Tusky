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
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
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

    val dialog = AlertDialog.Builder(context)
        .setIcon(R.drawable.ic_poll_24dp)
        .setTitle(R.string.create_poll_title)
        .setView(binding.root)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(android.R.string.ok, null)
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
    val durationLabels = context.resources.getStringArray(R.array.poll_duration_names).filterIndexed { index, _ -> durations[index] in minDuration..maxDuration }
    binding.pollDurationSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, durationLabels).apply {
        setDropDownViewResource(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item)
    }
    durations = durations.filter { it in minDuration..maxDuration }

    binding.addChoiceButton.setOnClickListener {
        if (adapter.itemCount < maxOptionCount) {
            adapter.addChoice()
        }
        if (adapter.itemCount >= maxOptionCount) {
            it.isEnabled = false
        }
    }

    val pollDurationId = durations.indexOfLast {
        it <= (poll?.expiresIn ?: 0)
    }

    binding.pollDurationSpinner.setSelection(pollDurationId)

    binding.multipleChoicesCheckBox.isChecked = poll?.multiple ?: false

    dialog.setOnShowListener {
        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        button.setOnClickListener {
            val selectedPollDurationId = binding.pollDurationSpinner.selectedItemPosition

            onUpdatePoll(
                NewPoll(
                    options = adapter.pollOptions,
                    expiresIn = durations[selectedPollDurationId],
                    multiple = binding.multipleChoicesCheckBox.isChecked
                )
            )

            dialog.dismiss()
        }
    }

    dialog.show()

    // make the dialog focusable so the keyboard does not stay behind it
    dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
}
