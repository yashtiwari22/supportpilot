package com.supportpilot.rag;

import com.supportpilot.common.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller managing tenant knowledge base articles and real-time chunk indexing.
 */
@RestController
@RequestMapping("/api/v1/kb/articles")
public class KbController {

    private final KbArticleRepository articleRepository;
    private final HybridSearchService searchService;

    public record CreateArticleRequest(
            @NotBlank(message = "Title is required") String title,
            @NotBlank(message = "Content is required") String content
    ) {}

    public KbController(KbArticleRepository articleRepository, HybridSearchService searchService) {
        this.articleRepository = articleRepository;
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<List<KbArticleRepository.ArticleSummaryProjection>> listArticles() {
        UUID tenantId = TenantContext.requireTenantId();
        return ResponseEntity.ok(articleRepository.findArticleSummariesByTenantId(tenantId));
    }

    @PostMapping
    public ResponseEntity<KbArticle> createArticle(@Valid @RequestBody CreateArticleRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        String slug = generateSlug(request.title());

        KbArticle article = new KbArticle(tenantId, request.title(), slug, request.content());
        article = articleRepository.save(article);

        // Immediately chunk and generate vector embeddings
        searchService.indexArticle(article);

        return ResponseEntity.status(HttpStatus.CREATED).body(article);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteArticle(@PathVariable UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        articleRepository.findByIdAndTenantId(id, tenantId).ifPresent(article -> {
            articleRepository.delete(article);
        });
        return ResponseEntity.noContent().build();
    }

    private String generateSlug(String title) {
        String base = title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return base.isEmpty() ? "article-" + System.currentTimeMillis() : base + "-" + (System.currentTimeMillis() % 10000);
    }
}
