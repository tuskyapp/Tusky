package com.keylesspalace.tusky;

import java.io.IOException;
import java.util.List;

public interface FetchTimelineListener {
    void onFetchTimelineSuccess(List<Status> statuses, boolean added);
    void onFetchTimelineFailure(IOException e);
}
