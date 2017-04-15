package com.keylesspalace.tusky;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.widget.TextView;

public class AboutActivity extends AppCompatActivity {
    private TextView mVersionTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mVersionTextView = (TextView) findViewById(R.id.textView);
        String versionName = BuildConfig.VERSION_NAME;

        mVersionTextView.
                setText(getString(R.string.about_application_version)+ versionName);
    }

}
