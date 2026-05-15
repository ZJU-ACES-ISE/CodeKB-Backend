package com.codekb.knowledge;

import com.codekb.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository repo;

    public KnowledgeBaseService(KnowledgeBaseRepository repo) {
        this.repo = repo;
    }

    public List<KnowledgeBase> listByOwner(Long ownerId) {
        return repo.findByOwnerId(ownerId);
    }

    public List<KnowledgeBase> listAll() {
        return repo.findAll();
    }

    @Transactional
    public KnowledgeBase create(Long ownerId, String name, String description) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setOwnerId(ownerId);
        kb.setName(name);
        kb.setDescription(description);
        return repo.save(kb);
    }

    public KnowledgeBase getById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new BusinessException(404, "知识库不存在: " + id));
    }

    @Transactional
    public KnowledgeBase update(Long ownerId, Long id, String name, String description) {
        KnowledgeBase kb = getById(id);
        if (!kb.getOwnerId().equals(ownerId)) {
            throw new BusinessException(403, "无权修改该知识库");
        }
        kb.setName(name);
        kb.setDescription(description);
        return repo.save(kb);
    }

    @Transactional
    public void incrementRepoCount(Long kbId) {
        repo.findById(kbId).ifPresent(kb -> {
            kb.setRepoCount(kb.getRepoCount() + 1);
            repo.save(kb);
        });
    }

    @Transactional
    public void decrementRepoCount(Long kbId) {
        repo.findById(kbId).ifPresent(kb -> {
            kb.setRepoCount(Math.max(0, kb.getRepoCount() - 1));
            repo.save(kb);
        });
    }
}
