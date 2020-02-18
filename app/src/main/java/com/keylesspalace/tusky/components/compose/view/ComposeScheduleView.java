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

package com.keylesspalace.tusky.components.compose.view;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.fragment.TimePickerFragment;
import com.keylesspalace.tusky.util.ThemeUtils;

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
    public static int MINIMUM_SCHEDULED_SECONDS = 330; // Minimum is 5 minutes, pad 30 seconds for posting

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
            scheduledDateTimeView.setText("");
        } else {
            scheduledDateTimeView.setText(String.format("%s %s",
                    dateFormat.format(scheduleDateTime.getTime()),
                    timeFormat.format(scheduleDateTime.getTime())));
        }
    }

    private void setEditIcons() {
        Drawable icon = ThemeUtils.getTintedDrawable(getContext(), R.drawable.ic_create_24dp, android.R.attr.textColorTertiary);
        if (icon == null) {
            return;
        }

        final int size = scheduledDateTimeView.getLineHeight();

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

    public void openPickDateDialog() {
        long yesterday = Calendar.getInstance().getTimeInMillis() - 24 * 60 * 60 * 1000;
        CalendarConstraints calendarConstraints = new CalendarConstraints.Builder()
                .setValidator(
                        DateValidatorPointForward.from(yesterday))
                .build();
        initializeSuggestedTime();
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

    public Date getDateTime(String scheduledAt) {
        try {
            return iso8601.parse(scheduledAt);
        } catch (ParseException e) {
            return null;
        }
    }

    public void setDateTime(String scheduledAt) {
        Date date;
        try {
            date = iso8601.parse(scheduledAt);
        } catch (ParseException e) {
            return;
        }
        initializeSuggestedTime();
        scheduleDateTime.setTime(date);
        setScheduledDateTime();
    }

    private void onDateSet(long selection) {
        initializeSuggestedTime();
        Calendar newDate = getCalendar();
        newDate.setTimeInMillis(selection);
        newDate.add(Calendar.MINUTE, 10);
        scheduleDateTime.set(newDate.get(Calendar.YEAR), newDate.get(Calendar.MONTH), newDate.get(Calendar.DATE));
        openPickTimeDialog();
    }

    public void onTimeSet(int hourOfDay, int minute) {
        initializeSuggestedTime();
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

    @NonNull
    public static Calendar getCalendar() {
        return Calendar.getInstance(TimeZone.getDefault());
    }

    private void initializeSuggestedTime() {
        if (scheduleDateTime == null) {
            scheduleDateTime = getCalendar();
            scheduleDateTime.add(Calendar.MINUTE, 15);
        }
    }
}
