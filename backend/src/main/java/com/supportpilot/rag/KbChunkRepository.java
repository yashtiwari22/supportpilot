package com.supportpilot.rag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for KbChunk with pgvector cosine similarity search.
 */
@Repository
public interface KbChunkRepository extends JpaRepository<KbChunk, UUID> {

    @Modifying
    @Query("DELETE FROM KbChunk c WHERE c.articleId = :articleId")
    void deleteByArticleId(@Param("articleId") UUID articleId);

    List<KbChunk> findByArticleIdOrderByChunkIndexAsc(UUID articleId);

    @Query(value = """
        SELECT c.id as id,
               c.article_id as articleId,
               a.title as articleTitle,
               c.chunk_index as chunkIndex,
               c.content as content,
               (1 - (c.embedding <=> cast(:queryVector as vector))) as similarityScore
        FROM kb_chunks c
        JOIN kb_articles a ON a.id = c.article_id
        WHERE c.tenant_id = :tenantId
          AND c.embedding IS NOT NULL
        ORDER BY c.embedding <=> cast(:queryVector as vector) ASC
        LIMIT :limit
    """, nativeQuery = true)
    List<ChunkSearchResultProjection> searchSimilarChunks(
            @Param("tenantId") UUID tenantId,
            @Param("queryVector") String queryVector,
            @Param("limit") int limit
    );

    @Query(value = """
        SELECT c.id as id,
               c.article_id as articleId,
               a.title as articleTitle,
               c.chunk_index as chunkIndex,
               c.content as content,
               1.0 as similarityScore
        FROM kb_chunks c
        JOIN kb_articles a ON a.id = c.article_id
        WHERE c.tenant_id = :tenantId
          AND (
            LOWER(c.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        LIMIT :limit
    """, nativeQuery = true)
    List<ChunkSearchResultProjection> searchByKeyword(
            @Param("tenantId") UUID tenantId,
            @Param("keyword") String keyword,
            @Param("limit") int limit
    );

    interface ChunkSearchResultProjection {
        UUID getId();
        UUID getArticleId();
        String getArticleTitle();
        int getChunkIndex();
        String getContent();
        Double getSimilarityScore();
    }
}
