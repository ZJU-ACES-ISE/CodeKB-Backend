package com.codekb.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RepoSummaryRepository extends JpaRepository<RepoSummary, Long> {
    Optional<RepoSummary> findByRepoId(Long repoId);
    List<RepoSummary> findByRepoIdIn(Collection<Long> repoIds);

    @Modifying
    @Query("delete from RepoSummary s where s.repoId = :repoId")
    int deleteByRepoId(Long repoId);
}
