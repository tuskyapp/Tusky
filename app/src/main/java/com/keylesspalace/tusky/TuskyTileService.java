package com.keylesspalace.tusky;

import android.annotation.TargetApi;
import android.content.Intent;
import android.service.quicksettings.TileService;

/**
 * Small Addition that adds in a QuickSettings tile that opens the Compose activity when clicked
 * Created by ztepps on 4/3/17.
 */

@TargetApi(24)
public class TuskyTileService extends TileService {
    public TuskyTileService() {
        super();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        startActivity(new Intent(this, ComposeActivity.class));
    }
}
