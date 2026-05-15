package com.codekb.graph;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RepoGraphTaskRepository extends JpaRepository<RepoGraphTask, Long> {
    Optional<RepoGraphTask> findFirstByRepoIdOrderByCreatedAtDesc(Long repoId);
    Optional<RepoGraphTask> findFirstByRepoIdAndStatusOrderByCreatedAtDesc(Long repoId, GraphTaskStatus status);
    Optional<RepoGraphTask> findByGraphJobId(String graphJobId);

    List<RepoGraphTask> findByRepoId(Long repoId);

    @Modifying
    @Query("delete from RepoGraphTask t where t.repoId = :repoId")
    int deleteByRepoId(Long repoId);
}
