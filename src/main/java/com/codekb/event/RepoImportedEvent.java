package com.codekb.event;

import org.springframework.context.ApplicationEvent;

public class RepoImportedEvent extends ApplicationEvent {
    private final Long repoId;

    public RepoImportedEvent(Object source, Long repoId) {
        super(source);
        this.repoId = repoId;
    }

    public Long getRepoId() {
        return repoId;
    }
}
