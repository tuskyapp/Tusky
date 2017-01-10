package com.keylesspalace.tusky;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.MediaController;
import android.widget.VideoView;

public class ViewVideoActivity extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_video);
        String url = getIntent().getStringExtra("url");
        VideoView videoView = (VideoView) findViewById(R.id.video_player);
        videoView.setVideoPath(url);
        MediaController controller = new MediaController(this);
        videoView.setMediaController(controller);
        controller.show();
        videoView.start();
    }
}
