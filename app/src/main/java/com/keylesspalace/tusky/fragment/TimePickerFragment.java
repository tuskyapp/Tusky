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

package com.keylesspalace.tusky.fragment;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.keylesspalace.tusky.components.compose.ComposeActivity;

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
