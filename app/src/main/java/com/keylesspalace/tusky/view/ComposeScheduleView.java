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

package com.keylesspalace.tusky.view;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.fragment.TimePickerFragment;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class ComposeScheduleView extends ConstraintLayout {

    private DateFormat dateFormat;
    private DateFormat timeFormat;
    private SimpleDateFormat iso8601;

    private Button resetScheduleButton;
    private TextView scheduledDateTimeView;

    private Calendar scheduleDateTime;

    public ComposeScheduleView(Context context) {
        super(context);
        init();
    }

    public ComposeScheduleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ComposeScheduleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.view_compose_schedule, this);

        dateFormat = SimpleDateFormat.getDateInstance();
        timeFormat = SimpleDateFormat.getTimeInstance();
        iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
        iso8601.setTimeZone(TimeZone.getTimeZone("UTC"));

        resetScheduleButton = findViewById(R.id.resetScheduleButton);
        scheduledDateTimeView = findViewById(R.id.scheduledDateTime);

        scheduledDateTimeView.setOnClickListener(v -> openPickDateDialog());

        scheduleDateTime = null;

        setScheduledDateTime();

        setEditIcons();
    }

    private void setScheduledDateTime() {
        if (scheduleDateTime == null) {
            scheduledDateTimeView.setText(R.string.hint_configure_scheduled_toot);
        } else {
            scheduledDateTimeView.setText(String.format("%s %s",
                    dateFormat.format(scheduleDateTime.getTime()),
                    timeFormat.format(scheduleDateTime.getTime())));
        }
    }

    private void setEditIcons() {
        final int size = scheduledDateTimeView.getLineHeight();

        Drawable icon = getContext().getDrawable(R.drawable.ic_create_24dp);
        if (icon == null) {
            return;
        }

        icon.setBounds(0, 0, size, size);

        scheduledDateTimeView.setCompoundDrawables(null, null, icon, null);
    }

    public void setResetOnClickListener(OnClickListener listener) {
        resetScheduleButton.setOnClickListener(listener);
    }

    public void resetSchedule() {
        scheduleDateTime = null;
        setScheduledDateTime();
    }

    private void openPickDateDialog() {
        long yesterday = Calendar.getInstance().getTimeInMillis() - 24 * 60 * 60 * 1000;
        CalendarConstraints calendarConstraints = new CalendarConstraints.Builder()
                .setValidator(new DateValidatorPointForward(yesterday))
                .build();
        if (scheduleDateTime == null) {
            scheduleDateTime = Calendar.getInstance(TimeZone.getDefault());
        }
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder
                .datePicker()
                .setSelection(scheduleDateTime.getTimeInMillis())
                .setCalendarConstraints(calendarConstraints)
                .build();
        picker.addOnPositiveButtonClickListener(this::onDateSet);
        picker.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "date_picker");
    }

    private void openPickTimeDialog() {
        TimePickerFragment picker = new TimePickerFragment();
        if (scheduleDateTime != null) {
            Bundle args = new Bundle();
            args.putInt(TimePickerFragment.PICKER_TIME_HOUR, scheduleDateTime.get(Calendar.HOUR_OF_DAY));
            args.putInt(TimePickerFragment.PICKER_TIME_MINUTE, scheduleDateTime.get(Calendar.MINUTE));
            picker.setArguments(args);
        }
        picker.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "time_picker");
    }

    public void setDateTime(String scheduledAt) {
        Date date;
        try {
            date = iso8601.parse(scheduledAt);
        } catch (ParseException e) {
            return;
        }
        if (scheduleDateTime == null) {
            scheduleDateTime = Calendar.getInstance(TimeZone.getDefault());
        }
        scheduleDateTime.setTime(date);
        setScheduledDateTime();
    }

    private void onDateSet(long selection) {
        if (scheduleDateTime == null) {
            scheduleDateTime = Calendar.getInstance(TimeZone.getDefault());
        }
        Calendar newDate = Calendar.getInstance(TimeZone.getDefault());
        newDate.setTimeInMillis(selection);
        scheduleDateTime.set(newDate.get(Calendar.YEAR), newDate.get(Calendar.MONTH), newDate.get(Calendar.DATE));
        openPickTimeDialog();
    }

    public void onTimeSet(int hourOfDay, int minute) {
        if (scheduleDateTime == null) {
            scheduleDateTime = Calendar.getInstance(TimeZone.getDefault());
        }
        scheduleDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
        scheduleDateTime.set(Calendar.MINUTE, minute);
        setScheduledDateTime();
    }

    public String getTime() {
        if (scheduleDateTime == null) {
            return null;
        }
        return iso8601.format(scheduleDateTime.getTime());
    }
}
