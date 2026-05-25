package com.codekb.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DefaultKnowledgeBaseInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultKnowledgeBaseInitializer.class);

    private final KnowledgeBaseService knowledgeBaseService;

    public DefaultKnowledgeBaseInitializer(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    public void run(String... args) {
        int created = knowledgeBaseService.ensureDefaultKnowledgeBaseForAllUsers();
        if (created > 0) {
            log.info("Created {} default private knowledge bases", created);
        }
    }
}
