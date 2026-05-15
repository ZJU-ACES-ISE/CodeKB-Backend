package com.codekb.repo;

public record RepoUrlParts(
        RepoProvider provider,
        String host,
        String owner,
        String repo,
        String name,
        String projectPath
) {
    public boolean hasRemoteProject() {
        return owner != null && !owner.isBlank() && repo != null && !repo.isBlank();
    }
}
