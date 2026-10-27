package com.supportpilot.rag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for KbArticle entities.
 */
@Repository
public interface KbArticleRepository extends JpaRepository<KbArticle, UUID> {

    List<KbArticle> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<KbArticle> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<KbArticle> findByTenantIdAndSlug(UUID tenantId, String slug);

    @Query("""
        SELECT a.id as id, a.title as title, a.slug as slug, a.createdAt as createdAt, COUNT(c.id) as chunkCount
        FROM KbArticle a
        LEFT JOIN KbChunk c ON c.articleId = a.id
        WHERE a.tenantId = :tenantId
        GROUP BY a.id, a.title, a.slug, a.createdAt
        ORDER BY a.createdAt DESC
    """)
    List<ArticleSummaryProjection> findArticleSummariesByTenantId(@Param("tenantId") UUID tenantId);

    interface ArticleSummaryProjection {
        UUID getId();
        String getTitle();
        String getSlug();
        java.time.Instant getCreatedAt();
        long getChunkCount();
    }
}
