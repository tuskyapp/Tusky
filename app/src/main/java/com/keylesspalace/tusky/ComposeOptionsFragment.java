package com.keylesspalace.tusky;

import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioGroup;

public class ComposeOptionsFragment extends BottomSheetDialogFragment {
    public interface Listener extends Parcelable {
        void onVisibilityChanged(String visibility);
        void onMarkSensitiveChanged(boolean markSensitive);
        void onContentWarningChanged(boolean hideText);
    }

    private Listener listener;

    public static ComposeOptionsFragment newInstance(String visibility, boolean markSensitive,
            boolean hideText, boolean showMarkSensitive, Listener listener) {
        Bundle arguments = new Bundle();
        ComposeOptionsFragment fragment = new ComposeOptionsFragment();
        arguments.putParcelable("listener", listener);
        arguments.putString("visibility", visibility);
        arguments.putBoolean("markSensitive", markSensitive);
        arguments.putBoolean("hideText", hideText);
        arguments.putBoolean("showMarkSensitive", showMarkSensitive);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_compose_options, container, false);

        Bundle arguments = getArguments();
        listener = arguments.getParcelable("listener");
        String statusVisibility = arguments.getString("visibility");
        boolean statusMarkSensitive = arguments.getBoolean("markSensitive");
        boolean statusHideText = arguments.getBoolean("hideText");
        boolean showMarkSensitive = arguments.getBoolean("showMarkSensitive");

        RadioGroup radio = (RadioGroup) rootView.findViewById(R.id.radio_visibility);
        int radioCheckedId = R.id.radio_public;
        if (statusVisibility != null) {
            if (statusVisibility.equals("unlisted")) {
                radioCheckedId = R.id.radio_unlisted;
            } else if (statusVisibility.equals("private")) {
                radioCheckedId = R.id.radio_private;
            }
        }
        radio.check(radioCheckedId);
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
                }
                listener.onVisibilityChanged(visibility);
            }
        });

        CheckBox markSensitive = (CheckBox) rootView.findViewById(R.id.compose_mark_sensitive);
        if (showMarkSensitive) {
            markSensitive.setChecked(statusMarkSensitive);
            markSensitive.setEnabled(true);
            markSensitive.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    listener.onMarkSensitiveChanged(isChecked);
                }
            });
        } else {
            markSensitive.setEnabled(false);
        }

        CheckBox hideText = (CheckBox) rootView.findViewById(R.id.compose_hide_text);
        hideText.setChecked(statusHideText);
        hideText.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                listener.onContentWarningChanged(isChecked);
            }
        });

        return rootView;
    }
}
