package com.codekb.event;

import org.springframework.context.ApplicationEvent;

public class ZipGraphUploadRequestedEvent extends ApplicationEvent {
    private final Long repoId;
    private final byte[] zipBytes;
    private final String originalFilename;
    private final String repoNameOverride;

    public ZipGraphUploadRequestedEvent(Object source,
                                        Long repoId,
                                        byte[] zipBytes,
                                        String originalFilename,
                                        String repoNameOverride) {
        super(source);
        this.repoId = repoId;
        this.zipBytes = zipBytes;
        this.originalFilename = originalFilename;
        this.repoNameOverride = repoNameOverride;
    }

    public Long getRepoId() {
        return repoId;
    }

    public byte[] getZipBytes() {
        return zipBytes;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getRepoNameOverride() {
        return repoNameOverride;
    }
}
