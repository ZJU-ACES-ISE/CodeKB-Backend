package com.codekb.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {
    List<KnowledgeBase> findByOwnerId(Long ownerId);
    Optional<KnowledgeBase> findByIdAndOwnerId(Long id, Long ownerId);
}
