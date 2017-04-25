package com.keylesspalace.tusky;

interface LinkListener {
    void onViewTag(String tag);
    void onViewAccount(String id);
}
