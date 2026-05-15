package com.codekb.event;

import org.springframework.context.ApplicationEvent;

public class GraphJobRequestedEvent extends ApplicationEvent {
    private final Long repoId;

    public GraphJobRequestedEvent(Object source, Long repoId) {
        super(source);
        this.repoId = repoId;
    }

    public Long getRepoId() {
        return repoId;
    }
}
