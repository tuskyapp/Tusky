package com.keylesspalace.tusky;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;

public class ViewMediaFragment extends Fragment {
    public static ViewMediaFragment newInstance(String url) {
        Bundle arguments = new Bundle();
        ViewMediaFragment fragment = new ViewMediaFragment();
        arguments.putString("url", url);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_view_media, container, false);

        Bundle arguments = getArguments();
        String url = arguments.getString("url");
        NetworkImageView image = (NetworkImageView) rootView.findViewById(R.id.view_media_image);
        ImageLoader imageLoader = VolleySingleton.getInstance(getContext()).getImageLoader();
        image.setImageUrl(url, imageLoader);

        rootView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        return rootView;
    }

    private void dismiss() {
        getFragmentManager().popBackStack();
    }
}
