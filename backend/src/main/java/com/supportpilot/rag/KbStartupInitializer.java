package com.supportpilot.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Initializes and indexes seeded knowledge base articles on application startup if chunks are missing.
 */
@Component
public class KbStartupInitializer {

    private static final Logger log = LoggerFactory.getLogger(KbStartupInitializer.class);

    private final KbArticleRepository articleRepository;
    private final KbChunkRepository chunkRepository;
    private final HybridSearchService searchService;

    public KbStartupInitializer(
            KbArticleRepository articleRepository,
            KbChunkRepository chunkRepository,
            HybridSearchService searchService) {
        this.articleRepository = articleRepository;
        this.chunkRepository = chunkRepository;
        this.searchService = searchService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (chunkRepository.count() == 0) {
            log.info("No KB chunks found. Indexing seeded articles...");
            List<KbArticle> articles = articleRepository.findAll();
            for (KbArticle article : articles) {
                try {
                    searchService.indexArticle(article);
                } catch (Exception e) {
                    log.error("Failed to index article on startup: {}", article.getTitle(), e);
                }
            }
            log.info("Successfully indexed {} seeded KB articles", articles.size());
        }
    }
}
