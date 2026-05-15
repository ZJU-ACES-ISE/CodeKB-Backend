package com.codekb.graph;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "repo_graph_task")
public class RepoGraphTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repo_id", nullable = false)
    private Long repoId;

    @Column(name = "graph_job_id", length = 64)
    private String graphJobId;

    @Column(name = "github_url", nullable = false, length = 500)
    private String githubUrl;

    @Column(length = 100)
    private String ref;

    private int depth = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GraphTaskStatus status = GraphTaskStatus.PENDING;

    @Column(name = "external_status_raw", length = 50)
    private String externalStatusRaw;

    @Column(name = "node_count")
    private Integer nodeCount;

    @Column(name = "edge_count")
    private Integer edgeCount;

    @Column(name = "snapshot_url", length = 500)
    private String snapshotUrl;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getRepoId() { return repoId; }
    public void setRepoId(Long repoId) { this.repoId = repoId; }
    public String getGraphJobId() { return graphJobId; }
    public void setGraphJobId(String graphJobId) { this.graphJobId = graphJobId; }
    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }
    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }
    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }
    public GraphTaskStatus getStatus() { return status; }
    public void setStatus(GraphTaskStatus status) { this.status = status; }
    public String getExternalStatusRaw() { return externalStatusRaw; }
    public void setExternalStatusRaw(String externalStatusRaw) { this.externalStatusRaw = externalStatusRaw; }
    public Integer getNodeCount() { return nodeCount; }
    public void setNodeCount(Integer nodeCount) { this.nodeCount = nodeCount; }
    public Integer getEdgeCount() { return edgeCount; }
    public void setEdgeCount(Integer edgeCount) { this.edgeCount = edgeCount; }
    public String getSnapshotUrl() { return snapshotUrl; }
    public void setSnapshotUrl(String snapshotUrl) { this.snapshotUrl = snapshotUrl; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
