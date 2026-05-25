package com.codekb.knowledge;

import com.codekb.auth.User;
import com.codekb.auth.UserRepository;
import com.codekb.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository repo;
    private final UserRepository userRepository;

    public KnowledgeBaseService(KnowledgeBaseRepository repo, UserRepository userRepository) {
        this.repo = repo;
        this.userRepository = userRepository;
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

    public KnowledgeBase getOwnedById(Long ownerId, Long id) {
        return repo.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new BusinessException(404, "知识库不存在: " + id));
    }

    @Transactional
    public KnowledgeBase update(Long ownerId, Long id, String name, String description) {
        KnowledgeBase kb = getOwnedById(ownerId, id);
        kb.setName(name);
        kb.setDescription(description);
        return repo.save(kb);
    }

    @Transactional
    public KnowledgeBase ensureDefaultKnowledgeBase(Long ownerId) {
        List<KnowledgeBase> existing = repo.findByOwnerId(ownerId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        User user = userRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在: " + ownerId));
        KnowledgeBase kb = new KnowledgeBase();
        kb.setOwnerId(ownerId);
        kb.setName(defaultKnowledgeBaseName(user));
        kb.setDescription("默认私有知识库");
        return repo.save(kb);
    }

    @Transactional
    public int ensureDefaultKnowledgeBaseForAllUsers() {
        int created = 0;
        for (User user : userRepository.findAll()) {
            if (repo.findByOwnerId(user.getId()).isEmpty()) {
                KnowledgeBase kb = new KnowledgeBase();
                kb.setOwnerId(user.getId());
                kb.setName(defaultKnowledgeBaseName(user));
                kb.setDescription("默认私有知识库");
                repo.save(kb);
                created++;
            }
        }
        return created;
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

    private String defaultKnowledgeBaseName(User user) {
        String base = user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName().trim()
                : user.getUsername();
        return base + "知识库";
    }
}
