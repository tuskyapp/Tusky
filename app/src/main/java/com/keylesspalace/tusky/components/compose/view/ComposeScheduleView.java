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
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.keylesspalace.tusky.R;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class ComposeScheduleView extends ConstraintLayout {

    public interface OnTimeSetListener {
        void onTimeSet(String time);
    }

    private OnTimeSetListener listener;

    private DateFormat dateFormat;
    private DateFormat timeFormat;
    private SimpleDateFormat iso8601;

    private Button resetScheduleButton;
    private TextView scheduledDateTimeView;
    private TextView invalidScheduleWarningView;

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
        invalidScheduleWarningView = findViewById(R.id.invalidScheduleWarning);

        scheduledDateTimeView.setOnClickListener(v -> openPickDateDialog());
        invalidScheduleWarningView.setText(R.string.warning_scheduling_interval);

        scheduleDateTime = null;

        setScheduledDateTime();

        setEditIcons();
    }

    public void setListener(OnTimeSetListener listener) {
        this.listener = listener;
    }

    private void setScheduledDateTime() {
        if (scheduleDateTime == null) {
            scheduledDateTimeView.setText("");
            invalidScheduleWarningView.setVisibility(GONE);
        } else {
            Date scheduled = scheduleDateTime.getTime();
            scheduledDateTimeView.setText(String.format("%s %s",
                    dateFormat.format(scheduled),
                    timeFormat.format(scheduled)));
            verifyScheduledTime(scheduled);
        }
    }

    private void setEditIcons() {
        Drawable icon = ContextCompat.getDrawable(getContext(), R.drawable.ic_create_24dp);
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
        MaterialTimePicker.Builder pickerBuilder = new MaterialTimePicker.Builder();
        if (scheduleDateTime != null) {
            pickerBuilder.setHour(scheduleDateTime.get(Calendar.HOUR_OF_DAY))
                    .setMinute(scheduleDateTime.get(Calendar.MINUTE));
        }
        if (android.text.format.DateFormat.is24HourFormat(this.getContext())) {
            pickerBuilder.setTimeFormat(TimeFormat.CLOCK_24H);
        } else {
            pickerBuilder.setTimeFormat(TimeFormat.CLOCK_12H);
        }

        MaterialTimePicker picker = pickerBuilder.build();
        picker.addOnPositiveButtonClickListener(v -> onTimeSet(picker.getHour(), picker.getMinute()));

        picker.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "time_picker");
    }

    public Date getDateTime(String scheduledAt) {
        if (scheduledAt != null) {
            try {
                return iso8601.parse(scheduledAt);
            } catch (ParseException e) {
            }
        }
        return null;
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

    public boolean verifyScheduledTime(@Nullable Date scheduledTime) {
        boolean valid;
        if (scheduledTime != null) {
            Calendar minimumScheduledTime = getCalendar();
            minimumScheduledTime.add(Calendar.SECOND, MINIMUM_SCHEDULED_SECONDS);
            valid = scheduledTime.after(minimumScheduledTime.getTime());
        } else {
            valid = true;
        }
        invalidScheduleWarningView.setVisibility(valid ? GONE : VISIBLE);
        return valid;
    }

    private void onDateSet(long selection) {
        initializeSuggestedTime();
        Calendar newDate = getCalendar();
        // working around bug in DatePicker where date is UTC #1720
        // see https://github.com/material-components/material-components-android/issues/882
        newDate.setTimeZone(TimeZone.getTimeZone("UTC"));
        newDate.setTimeInMillis(selection);
        scheduleDateTime.set(newDate.get(Calendar.YEAR), newDate.get(Calendar.MONTH), newDate.get(Calendar.DATE));
        openPickTimeDialog();
    }

    private void onTimeSet(int hourOfDay, int minute) {
        initializeSuggestedTime();
        scheduleDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
        scheduleDateTime.set(Calendar.MINUTE, minute);
        setScheduledDateTime();
        if (listener != null) {
            listener.onTimeSet(getTime());
        }
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
