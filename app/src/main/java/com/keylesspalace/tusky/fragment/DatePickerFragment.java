package com.keylesspalace.tusky.fragment;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.os.Bundle;

import com.keylesspalace.tusky.ComposeActivity;

import java.util.Calendar;
import java.util.TimeZone;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

public class DatePickerFragment extends DialogFragment {

    public static final String PICKER_TIME_YEAR = "picker_time_year";
    public static final String PICKER_TIME_MONTH = "picker_time_month";
    public static final String PICKER_TIME_DAY = "picker_time_day";

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        if (args != null) {
            calendar.set(args.getInt(PICKER_TIME_YEAR),
                    args.getInt(PICKER_TIME_MONTH),
                    args.getInt(PICKER_TIME_DAY));
        }

        return new DatePickerDialog(getContext(),
                android.R.style.Theme_DeviceDefault_Dialog,
                (ComposeActivity)getActivity(),
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
    }

}
