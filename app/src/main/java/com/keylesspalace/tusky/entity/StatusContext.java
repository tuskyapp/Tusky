package com.keylesspalace.tusky.entity;

import com.keylesspalace.tusky.Status;

import java.util.List;

public class StatusContext {
    public List<Status> ancestors;
    public List<Status> descendants;
}
