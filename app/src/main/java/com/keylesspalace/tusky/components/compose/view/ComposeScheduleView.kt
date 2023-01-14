/* Copyright 2019 kyori19
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
package com.keylesspalace.tusky.components.compose.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ViewComposeScheduleBinding
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ComposeScheduleView
@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    interface OnTimeSetListener {
        fun onTimeSet(time: String?)
    }

    private var binding = ViewComposeScheduleBinding.inflate(
        (context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater),
        this
    )
    private var listener: OnTimeSetListener? = null
    private var dateFormat = SimpleDateFormat.getDateInstance()
    private var timeFormat = SimpleDateFormat.getTimeInstance()
    private var iso8601 = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        Locale.getDefault()
    ).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private var scheduleDateTime: Calendar? = null

    init {
        binding.scheduledDateTime.setOnClickListener { openPickDateDialog() }
        binding.invalidScheduleWarning.setText(R.string.warning_scheduling_interval)
        updateScheduleUi()
        setEditIcons()
    }

    fun setListener(listener: OnTimeSetListener?) {
        this.listener = listener
    }

    private fun updateScheduleUi() {
        if (scheduleDateTime == null) {
            binding.scheduledDateTime.text = ""
            binding.invalidScheduleWarning.visibility = GONE
            return
        }

        val scheduled = scheduleDateTime!!.time
        binding.scheduledDateTime.text = String.format(
            "%s %s",
            dateFormat.format(scheduled),
            timeFormat.format(scheduled)
        )
        verifyScheduledTime(scheduled)
    }

    private fun setEditIcons() {
        val icon = ContextCompat.getDrawable(context, R.drawable.ic_create_24dp) ?: return
        val size = binding.scheduledDateTime.lineHeight
        icon.setBounds(0, 0, size, size)
        binding.scheduledDateTime.setCompoundDrawables(null, null, icon, null)
    }

    fun setResetOnClickListener(listener: OnClickListener?) {
        binding.resetScheduleButton.setOnClickListener(listener)
    }

    fun resetSchedule() {
        scheduleDateTime = null
        updateScheduleUi()
    }

    fun openPickDateDialog() {
        val yesterday = Calendar.getInstance().timeInMillis - 24 * 60 * 60 * 1000
        val calendarConstraints = CalendarConstraints.Builder()
            .setValidator(
                DateValidatorPointForward.from(yesterday)
            )
            .build()
        initializeSuggestedTime()
        val picker = MaterialDatePicker.Builder
            .datePicker()
            .setSelection(scheduleDateTime!!.timeInMillis)
            .setCalendarConstraints(calendarConstraints)
            .build()
        picker.addOnPositiveButtonClickListener { selection: Long -> onDateSet(selection) }
        picker.show((context as AppCompatActivity).supportFragmentManager, "date_picker")
    }

    private fun getTimeFormat(context: Context): Int {
        return if (android.text.format.DateFormat.is24HourFormat(context)) {
            TimeFormat.CLOCK_24H
        } else {
            TimeFormat.CLOCK_12H
        }
    }

    private fun openPickTimeDialog() {
        val pickerBuilder = MaterialTimePicker.Builder()
        scheduleDateTime?.let {
            pickerBuilder.setHour(it[Calendar.HOUR_OF_DAY])
                .setMinute(it[Calendar.MINUTE])
        }

        pickerBuilder.setTimeFormat(getTimeFormat(context))

        val picker = pickerBuilder.build()
        picker.addOnPositiveButtonClickListener { onTimeSet(picker.hour, picker.minute) }
        picker.show((context as AppCompatActivity).supportFragmentManager, "time_picker")
    }

    fun getDateTime(scheduledAt: String?): Date? {
        scheduledAt?.let {
            try {
                return iso8601.parse(it)
            } catch (_: ParseException) {
            }
        }
        return null
    }

    fun setDateTime(scheduledAt: String?) {
        val date = getDateTime(scheduledAt) ?: return
        initializeSuggestedTime()
        scheduleDateTime!!.time = date
        updateScheduleUi()
    }

    fun verifyScheduledTime(scheduledTime: Date?): Boolean {
        val valid: Boolean = if (scheduledTime != null) {
            val minimumScheduledTime = calendar()
            minimumScheduledTime.add(
                Calendar.SECOND,
                MINIMUM_SCHEDULED_SECONDS
            )
            scheduledTime.after(minimumScheduledTime.time)
        } else {
            true
        }
        binding.invalidScheduleWarning.visibility = if (valid) GONE else VISIBLE
        return valid
    }

    private fun onDateSet(selection: Long) {
        initializeSuggestedTime()
        val newDate = calendar()
        // working around bug in DatePicker where date is UTC #1720
        // see https://github.com/material-components/material-components-android/issues/882
        newDate.timeZone = TimeZone.getTimeZone("UTC")
        newDate.timeInMillis = selection
        scheduleDateTime!![newDate[Calendar.YEAR], newDate[Calendar.MONTH]] = newDate[Calendar.DATE]
        openPickTimeDialog()
    }

    private fun onTimeSet(hourOfDay: Int, minute: Int) {
        initializeSuggestedTime()
        scheduleDateTime?.set(Calendar.HOUR_OF_DAY, hourOfDay)
        scheduleDateTime?.set(Calendar.MINUTE, minute)
        updateScheduleUi()
        listener?.onTimeSet(time)
    }

    val time: String?
        get() = scheduleDateTime?.time?.let { iso8601.format(it) }

    private fun initializeSuggestedTime() {
        if (scheduleDateTime == null) {
            scheduleDateTime = calendar().apply {
                add(Calendar.MINUTE, 15)
            }
        }
    }

    companion object {
        var MINIMUM_SCHEDULED_SECONDS = 330 // Minimum is 5 minutes, pad 30 seconds for posting
        fun calendar(): Calendar = Calendar.getInstance(TimeZone.getDefault())
    }
}
