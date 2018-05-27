/* Copyright 2017 Andrew Dawson
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

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;

public class BaseFragment extends Fragment {
    protected List<Call> callList;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        callList = new ArrayList<>();
        handler.post(this::onPostCreate);
    }

    /**
     * For actions which should happen only once per lifecycle but after onCreate.
     * Example: subscribe for events in {@code onCreate()} but need dependencies to be injected
     */
    public void onPostCreate() {
        // No-op
    }

    @Override
    public void onDestroy() {
        for (Call call : callList) {
            call.cancel();
        }
        super.onDestroy();
    }
}
