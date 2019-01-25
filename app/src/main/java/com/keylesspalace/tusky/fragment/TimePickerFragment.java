package com.keylesspalace.tusky.fragment;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.keylesspalace.tusky.ComposeActivity;

import java.util.Calendar;
import java.util.TimeZone;

public class TimePickerFragment extends DialogFragment {

    public static final String PICKER_TIME_HOUR = "picker_time_hour";
    public static final String PICKER_TIME_MINUTE = "picker_time_minute";

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        if (args != null) {
            calendar.set(Calendar.HOUR_OF_DAY, args.getInt(PICKER_TIME_HOUR));
            calendar.set(Calendar.MINUTE, args.getInt(PICKER_TIME_MINUTE));
        }

        return new TimePickerDialog(getContext(),
                android.R.style.Theme_DeviceDefault_Dialog,
                (ComposeActivity) getActivity(),
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true);
    }

}
