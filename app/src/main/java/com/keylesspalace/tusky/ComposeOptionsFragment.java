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

package com.keylesspalace.tusky;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;

public class ComposeOptionsFragment extends BottomSheetDialogFragment {
    interface Listener {
        void onVisibilityChanged(String visibility);
        void onContentWarningChanged(boolean hideText);
    }

    private RadioGroup radio;
    private CheckBox hideText;
    private Listener listener;

    public static ComposeOptionsFragment newInstance(String visibility, boolean hideText,
            boolean isReply) {
        Bundle arguments = new Bundle();
        ComposeOptionsFragment fragment = new ComposeOptionsFragment();
        arguments.putString("visibility", visibility);
        arguments.putBoolean("hideText", hideText);
        arguments.putBoolean("isReply", isReply);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        listener = (Listener) context;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_compose_options, container, false);

        Bundle arguments = getArguments();
        String statusVisibility = arguments.getString("visibility");
        boolean statusHideText = arguments.getBoolean("hideText");
        boolean isReply = arguments.getBoolean("isReply");

        radio = (RadioGroup) rootView.findViewById(R.id.radio_visibility);
        int radioCheckedId;
        if (!isReply) {
            radioCheckedId = R.id.radio_public;
        } else {
            radioCheckedId = R.id.radio_unlisted;
        }
        if (statusVisibility != null) {
            if (statusVisibility.equals("public")) {
                radioCheckedId = R.id.radio_public;
            } else if (statusVisibility.equals("private")) {
                radioCheckedId = R.id.radio_private;
            } else if (statusVisibility.equals("unlisted")) {
                radioCheckedId = R.id.radio_unlisted;
            } else if (statusVisibility.equals("direct")) {
                radioCheckedId = R.id.radio_direct;
            }
        }
        radio.check(radioCheckedId);

        if (isReply) {
            RadioButton publicButton = (RadioButton) rootView.findViewById(R.id.radio_public);
            publicButton.setEnabled(false);
        }

        hideText = (CheckBox) rootView.findViewById(R.id.compose_hide_text);
        hideText.setChecked(statusHideText);

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        radio.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                String visibility;
                switch (checkedId) {
                    default:
                    case R.id.radio_public: {
                        visibility = "public";
                        break;
                    }
                    case R.id.radio_unlisted: {
                        visibility = "unlisted";
                        break;
                    }
                    case R.id.radio_private: {
                        visibility = "private";
                        break;
                    }
                    case R.id.radio_direct: {
                        visibility = "direct";
                        break;
                    }
                }
                listener.onVisibilityChanged(visibility);
            }
        });
        hideText.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                listener.onContentWarningChanged(isChecked);
            }
        });
    }
}
