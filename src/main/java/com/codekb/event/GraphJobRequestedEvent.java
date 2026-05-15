package com.codekb.event;

import org.springframework.context.ApplicationEvent;

public class GraphJobRequestedEvent extends ApplicationEvent {
    private final Long repoId;
    private final Long taskId;

    public GraphJobRequestedEvent(Object source, Long repoId) {
        this(source, repoId, null);
    }

    public GraphJobRequestedEvent(Object source, Long repoId, Long taskId) {
        super(source);
        this.repoId = repoId;
        this.taskId = taskId;
    }

    public Long getRepoId() {
        return repoId;
    }

    public Long getTaskId() {
        return taskId;
    }
}
