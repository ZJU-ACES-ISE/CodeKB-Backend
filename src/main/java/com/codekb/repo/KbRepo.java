package com.codekb.repo;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "kb_repo")
public class KbRepo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kb_id", nullable = false)
    private Long kbId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String owner;

    @Column(length = 100)
    private String repo;

    @Column(length = 30)
    private String provider;

    @Column(name = "github_url", nullable = false, length = 500)
    private String githubUrl;

    @Column(length = 100)
    private String ref;

    @Column(name = "default_branch", length = 100)
    private String defaultBranch;

    @Column(length = 50)
    private String language;

    @Column(length = 100)
    private String framework;

    @Column(name = "star_count")
    private Integer starCount;

    @Column(nullable = false, length = 20)
    private String status = "IMPORTED";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getRepo() { return repo; }
    public void setRepo(String repo) { this.repo = repo; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }
    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }
    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getFramework() { return framework; }
    public void setFramework(String framework) { this.framework = framework; }
    public Integer getStarCount() { return starCount; }
    public void setStarCount(Integer starCount) { this.starCount = starCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
