package com.keylesspalace.tusky.entity;

import com.keylesspalace.tusky.Status;

import java.util.List;

public class StatusContext {
    List<Status> ancestors;

    public List<Status> getAncestors() {
        return ancestors;
    }

    public void setAncestors(List<Status> ancestors) {
        this.ancestors = ancestors;
    }

    public List<Status> getDescendants() {
        return descendants;
    }

    public void setDescendants(List<Status> descendants) {
        this.descendants = descendants;
    }

    List<Status> descendants;
}
