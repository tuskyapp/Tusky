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

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetDialogFragment;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.util.ThemeUtils;

public class ComposeOptionsFragment extends BottomSheetDialogFragment {
    public interface Listener {
        void onVisibilityChanged(Status.Visibility visibility);
        void onContentWarningChanged(boolean hideText);
    }

    private RadioGroup radio;
    private CheckBox hideText;
    private Listener listener;

    public static ComposeOptionsFragment newInstance(Status.Visibility visibility, boolean hideText) {
        Bundle arguments = new Bundle();
        ComposeOptionsFragment fragment = new ComposeOptionsFragment();
        arguments.putInt("visibilityNum", visibility.getNum());
        arguments.putBoolean("hideText", hideText);
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
        Status.Visibility visibility = Status.Visibility.byNum(
                arguments.getInt("visibilityNum", 0)
        );
        boolean statusHideText = arguments.getBoolean("hideText");

        radio = rootView.findViewById(R.id.radio_visibility);
        int radioCheckedId = R.id.radio_public;
        switch (visibility) {
            case PUBLIC:   radioCheckedId = R.id.radio_public;   break;
            case PRIVATE:  radioCheckedId = R.id.radio_private;  break;
            case UNLISTED: radioCheckedId = R.id.radio_unlisted; break;
            case DIRECT:   radioCheckedId = R.id.radio_direct;   break;
        }
        radio.check(radioCheckedId);

        RadioButton publicButton = rootView.findViewById(R.id.radio_public);
        RadioButton unlistedButton = rootView.findViewById(R.id.radio_unlisted);
        RadioButton privateButton = rootView.findViewById(R.id.radio_private);
        RadioButton directButton = rootView.findViewById(R.id.radio_direct);
        setRadioButtonDrawable(getContext(), publicButton, R.drawable.ic_public_24dp);
        setRadioButtonDrawable(getContext(), unlistedButton, R.drawable.ic_lock_open_24dp);
        setRadioButtonDrawable(getContext(), privateButton, R.drawable.ic_lock_outline_24dp);
        setRadioButtonDrawable(getContext(), directButton, R.drawable.ic_email_24dp);

        hideText = rootView.findViewById(R.id.compose_hide_text);
        hideText.setChecked(statusHideText);

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        radio.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Status.Visibility visibility;
                switch (checkedId) {
                    default:
                    case R.id.radio_public: {
                        visibility = Status.Visibility.PUBLIC;
                        break;
                    }
                    case R.id.radio_unlisted: {
                        visibility = Status.Visibility.UNLISTED;
                        break;
                    }
                    case R.id.radio_private: {
                        visibility = Status.Visibility.PRIVATE;
                        break;
                    }
                    case R.id.radio_direct: {
                        visibility = Status.Visibility.DIRECT;
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

    private static void setRadioButtonDrawable(Context context, RadioButton button,
                                               @DrawableRes int id) {
        ColorStateList list = new ColorStateList(new int[][] {
                new int[] { -android.R.attr.state_checked },
                new int[] { android.R.attr.state_checked }
        }, new int[] {
                ThemeUtils.getColor(context, R.attr.compose_image_button_tint),
                ThemeUtils.getColor(context, R.attr.colorAccent)
        });
        Drawable drawable = VectorDrawableCompat.create(context.getResources(), id,
                context.getTheme());
        if (drawable == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setButtonTintList(list);
        } else {
            drawable = DrawableCompat.wrap(drawable);
            DrawableCompat.setTintList(drawable, list);
        }
        button.setButtonDrawable(drawable);
    }
}
