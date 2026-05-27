package com.codekb.graph;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RepoGraphTaskRepository extends JpaRepository<RepoGraphTask, Long> {
    Optional<RepoGraphTask> findFirstByRepoIdOrderByCreatedAtDesc(Long repoId);
    Optional<RepoGraphTask> findFirstByRepoIdAndStatusOrderByCreatedAtDesc(Long repoId, GraphTaskStatus status);
    Optional<RepoGraphTask> findByGraphJobId(String graphJobId);
    List<RepoGraphTask> findByRepoIdInOrderByRepoIdAscCreatedAtDesc(Collection<Long> repoIds);

    List<RepoGraphTask> findByRepoId(Long repoId);
    List<RepoGraphTask> findByRepoIdOrderByCreatedAtDesc(Long repoId);

    @Query("""
            select t from RepoGraphTask t
             where t.status in :statuses
               and (t.nextPollAt is null or t.nextPollAt <= :now)
               and (
                    t.leaseExpiresAt is null
                    or t.leaseExpiresAt <= :now
                    or (t.updatedAt is not null and t.updatedAt <= :staleBefore)
               )
             order by t.updatedAt asc
            """)
    List<RepoGraphTask> findDispatchableTasks(@Param("statuses") Collection<GraphTaskStatus> statuses,
                                              @Param("now") LocalDateTime now,
                                              @Param("staleBefore") LocalDateTime staleBefore,
                                              Pageable pageable);

    @Transactional
    @Modifying
    @Query("""
            update RepoGraphTask t
               set t.leaseExpiresAt = :leaseExpiresAt,
                   t.updatedAt = :now,
                   t.version = t.version + 1
             where t.id = :taskId
               and t.status in :statuses
               and (t.leaseExpiresAt is null or t.leaseExpiresAt <= :now)
            """)
    int tryAcquireLease(@Param("taskId") Long taskId,
                        @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
                        @Param("statuses") Collection<GraphTaskStatus> statuses,
                        @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from RepoGraphTask t where t.repoId = :repoId")
    int deleteByRepoId(Long repoId);
}
