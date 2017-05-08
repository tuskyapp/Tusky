package com.keylesspalace.tusky;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class AboutActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        TextView versionTextView = (TextView) findViewById(R.id.versionTV);
        Button mTuskyAccountButton = (Button) findViewById(R.id.tusky_profile_button);

        String versionName = BuildConfig.VERSION_NAME;
        String versionFormat = getString(R.string.about_application_version);
        versionTextView.setText(String.format(versionFormat, versionName));
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
