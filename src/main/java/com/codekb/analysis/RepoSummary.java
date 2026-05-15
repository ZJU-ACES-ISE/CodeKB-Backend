package com.codekb.analysis;

import com.fasterxml.jackson.annotation.JsonRawValue;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "repo_summary")
public class RepoSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repo_id", nullable = false, unique = true)
    private Long repoId;

    @Column(name = "primary_language", length = 50)
    private String primaryLanguage;

    @Column(columnDefinition = "JSON")
    private String frameworks;

    @Column(columnDefinition = "JSON")
    private String tags;

    @Column(columnDefinition = "JSON")
    private String topics;

    @Column(columnDefinition = "JSON")
    private String languages;

    @Column(name = "top_contributors", columnDefinition = "JSON")
    private String topContributors;

    @Column(name = "contributor_count")
    private Integer contributorCount;

    @Column(name = "fork_count")
    private Integer forkCount;

    @Column(name = "watchers_count")
    private Integer watchersCount;

    @Column(name = "open_issues_count")
    private Integer openIssuesCount;

    @Column(length = 50)
    private String license;

    @Column(name = "default_branch", length = 100)
    private String defaultBranch;

    @Column(length = 500)
    private String homepage;

    @Column(name = "size_kb")
    private Integer sizeKb;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "file_count")
    private int fileCount = 0;

    @Column(name = "code_line_count")
    private int codeLineCount = 0;

    @Column(name = "entry_files", columnDefinition = "JSON")
    private String entryFiles;

    @Column(name = "complexity_score")
    private Integer complexityScore;

    @Column(name = "summary_text", columnDefinition = "TEXT")
    private String summaryText;

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
    public String getPrimaryLanguage() { return primaryLanguage; }
    public void setPrimaryLanguage(String primaryLanguage) { this.primaryLanguage = primaryLanguage; }

    @JsonRawValue
    public String getFrameworks() { return frameworks; }
    public void setFrameworks(String frameworks) { this.frameworks = frameworks; }

    @JsonRawValue
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    @JsonRawValue
    public String getTopics() { return topics; }
    public void setTopics(String topics) { this.topics = topics; }

    @JsonRawValue
    public String getLanguages() { return languages; }
    public void setLanguages(String languages) { this.languages = languages; }

    @JsonRawValue
    public String getTopContributors() { return topContributors; }
    public void setTopContributors(String topContributors) { this.topContributors = topContributors; }

    public Integer getContributorCount() { return contributorCount; }
    public void setContributorCount(Integer contributorCount) { this.contributorCount = contributorCount; }
    public Integer getForkCount() { return forkCount; }
    public void setForkCount(Integer forkCount) { this.forkCount = forkCount; }
    public Integer getWatchersCount() { return watchersCount; }
    public void setWatchersCount(Integer watchersCount) { this.watchersCount = watchersCount; }
    public Integer getOpenIssuesCount() { return openIssuesCount; }
    public void setOpenIssuesCount(Integer openIssuesCount) { this.openIssuesCount = openIssuesCount; }
    public String getLicense() { return license; }
    public void setLicense(String license) { this.license = license; }
    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
    public String getHomepage() { return homepage; }
    public void setHomepage(String homepage) { this.homepage = homepage; }
    public Integer getSizeKb() { return sizeKb; }
    public void setSizeKb(Integer sizeKb) { this.sizeKb = sizeKb; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getFileCount() { return fileCount; }
    public void setFileCount(int fileCount) { this.fileCount = fileCount; }
    public int getCodeLineCount() { return codeLineCount; }
    public void setCodeLineCount(int codeLineCount) { this.codeLineCount = codeLineCount; }

    @JsonRawValue
    public String getEntryFiles() { return entryFiles; }
    public void setEntryFiles(String entryFiles) { this.entryFiles = entryFiles; }

    public Integer getComplexityScore() { return complexityScore; }
    public void setComplexityScore(Integer complexityScore) { this.complexityScore = complexityScore; }
    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
