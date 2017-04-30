package com.keylesspalace.tusky;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class AboutActivity extends AppCompatActivity {
    private TextView mVersionTextView;
    private TextView mProjectSiteTextView;
    private TextView mFeatureSiteTextView;
    private Button mTuskyAccountButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mVersionTextView = (TextView) findViewById(R.id.versionTV);
        mProjectSiteTextView = (TextView) findViewById(R.id.projectURL_TV);
        mFeatureSiteTextView = (TextView) findViewById(R.id.featuresURL_TV);
        mTuskyAccountButton = (Button) findViewById(R.id.tusky_profile_button);

        String versionName = BuildConfig.VERSION_NAME;

        mVersionTextView.setText(getString(R.string.about_application_version) + versionName);
        mTuskyAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAccountTVClick();
            }
        });
    }

    private void onAccountTVClick() {
        Intent intent = new Intent(this, AccountActivity.class);
        intent.putExtra("id", "72306");
        startActivity(intent);
    }
}
