package com.keylesspalace.tusky.view;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.fragment.DatePickerFragment;
import com.keylesspalace.tusky.fragment.TimePickerFragment;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class ComposeScheduleView extends ConstraintLayout {

    private DateFormat dateFormat;
    private DateFormat timeFormat;
    private SimpleDateFormat iso8601;

    private Button resetScheduleButton;
    private TextView scheduledDateView;
    private TextView scheduledTimeView;

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
        iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault());
        iso8601.setTimeZone(TimeZone.getTimeZone("UTC"));

        resetScheduleButton = findViewById(R.id.resetScheduleButton);
        scheduledDateView = findViewById(R.id.scheduledDate);
        scheduledTimeView = findViewById(R.id.scheduledTime);

        resetScheduleButton.setOnClickListener(v -> resetSchedule());
        scheduledDateView.setOnClickListener(v -> openPickDateDialog());
        scheduledTimeView.setOnClickListener(v -> openPickTimeDialog());

        scheduleDateTime = null;

        setScheduledDateTime();
    }

    private void setScheduledDateTime() {
        if (scheduleDateTime == null) {
            scheduledDateView.setText(R.string.hint_configure_scheduled_toot);
            scheduledTimeView.setText(R.string.hint_configure_scheduled_toot);
        } else {
            scheduledDateView.setText(dateFormat.format(scheduleDateTime.getTime()));
            scheduledTimeView.setText(timeFormat.format(scheduleDateTime.getTime()));
        }
    }

    private void resetSchedule() {
        scheduleDateTime = null;
        setScheduledDateTime();
    }

    private void openPickDateDialog() {
        DatePickerFragment picker = new DatePickerFragment();
        if (scheduleDateTime != null) {
            Bundle args = new Bundle();
            args.putInt(DatePickerFragment.PICKER_TIME_YEAR, scheduleDateTime.get(Calendar.YEAR));
            args.putInt(DatePickerFragment.PICKER_TIME_MONTH, scheduleDateTime.get(Calendar.MONTH));
            args.putInt(DatePickerFragment.PICKER_TIME_DAY, scheduleDateTime.get(Calendar.DAY_OF_MONTH));
            picker.setArguments(args);
        }
        picker.show(((AppCompatActivity) getContext()).getSupportFragmentManager(),
                "date_picker");
    }

    private void openPickTimeDialog() {
        TimePickerFragment picker = new TimePickerFragment();
        if (scheduleDateTime != null) {
            Bundle args = new Bundle();
            args.putInt(TimePickerFragment.PICKER_TIME_HOUR, scheduleDateTime.get(Calendar.HOUR_OF_DAY));
            args.putInt(TimePickerFragment.PICKER_TIME_MINUTE, scheduleDateTime.get(Calendar.MINUTE));
            picker.setArguments(args);
        }
        picker.show(((AppCompatActivity) getContext()).getSupportFragmentManager(),
                "time_picker");
    }

    public void onDateSet(int year, int month, int dayOfMonth) {
        if (scheduleDateTime == null) {
            scheduleDateTime = Calendar.getInstance(TimeZone.getDefault());
        }
        scheduleDateTime.set(year, month, dayOfMonth);
        setScheduledDateTime();
    }

    public void onTimeSet(int hourOfDay, int minute) {
        if (scheduleDateTime == null) {
            scheduleDateTime = Calendar.getInstance(TimeZone.getDefault());
        }
        scheduleDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
        scheduleDateTime.set(Calendar.MINUTE, minute);
        setScheduledDateTime();
    }

    public String getTime(){
        if (scheduleDateTime == null){
            return null;
        }
        return iso8601.format(scheduleDateTime.getTime());
    }
}
