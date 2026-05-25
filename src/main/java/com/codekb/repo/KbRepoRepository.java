package com.codekb.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface KbRepoRepository extends JpaRepository<KbRepo, Long> {
    List<KbRepo> findByKbId(Long kbId);
    List<KbRepo> findByKbIdIn(List<Long> kbIds);
    Optional<KbRepo> findByKbIdAndId(Long kbId, Long id);
    List<KbRepo> findByCreatedAtIsNullOrUpdatedAtIsNull();
    Optional<KbRepo> findFirstByCreatedByAndProviderIgnoreCaseAndOwnerIgnoreCaseAndRepoIgnoreCaseOrderByIdAsc(
            Long createdBy, String provider, String owner, String repo);
}
