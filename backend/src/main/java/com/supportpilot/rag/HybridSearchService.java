package com.supportpilot.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service performing hybrid search (dense pgvector cosine similarity + sparse keyword matching)
 * and managing knowledge base indexing.
 */
@Service
public class HybridSearchService {

    private static final Logger log = LoggerFactory.getLogger(HybridSearchService.class);

    private final KbArticleRepository articleRepository;
    private final KbChunkRepository chunkRepository;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;

    public record RetrievedChunk(
            UUID chunkId,
            UUID articleId,
            String articleTitle,
            int chunkIndex,
            String content,
            double score,
            String citationReference
    ) {}

    public HybridSearchService(
            KbArticleRepository articleRepository,
            KbChunkRepository chunkRepository,
            ChunkingService chunkingService,
            EmbeddingService embeddingService) {
        this.articleRepository = articleRepository;
        this.chunkRepository = chunkRepository;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
    }

    /**
     * Chunks and generates vector embeddings for a knowledge base article.
     */
    @Transactional
    public void indexArticle(KbArticle article) {
        // Remove existing chunks if re-indexing
        chunkRepository.deleteByArticleId(article.getId());

        List<ChunkingService.TextChunk> textChunks = chunkingService.chunkText(article.getContent());
        List<KbChunk> chunksToSave = new ArrayList<>();

        for (ChunkingService.TextChunk tc : textChunks) {
            String vectorStr = embeddingService.generateEmbeddingVector(tc.content());
            KbChunk chunk = new KbChunk(
                    article.getId(),
                    article.getTenantId(),
                    tc.index(),
                    tc.content(),
                    vectorStr
            );
            chunksToSave.add(chunk);
        }

        chunkRepository.saveAll(chunksToSave);
        log.info("Indexed article '{}' ({}) into {} chunks with pgvector embeddings",
                article.getTitle(), article.getId(), chunksToSave.size());
    }

    /**
     * Executes hybrid search combining pgvector cosine distance and keyword matching.
     */
    public List<RetrievedChunk> search(UUID tenantId, String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        Map<UUID, RetrievedChunk> combined = new LinkedHashMap<>();

        // 1. Keyword search (catches exact order numbers, model names, product SKUs)
        try {
            List<KbChunkRepository.ChunkSearchResultProjection> keywordResults =
                    chunkRepository.searchByKeyword(tenantId, query.trim(), topK);
            for (var res : keywordResults) {
                combined.put(res.getId(), new RetrievedChunk(
                        res.getId(),
                        res.getArticleId(),
                        res.getArticleTitle(),
                        res.getChunkIndex(),
                        res.getContent(),
                        0.95,
                        String.format("[%s - Chunk %d]", res.getArticleTitle(), res.getChunkIndex())
                ));
            }
        } catch (Exception e) {
            log.warn("Keyword search failed for query '{}': {}", query, e.getMessage());
        }

        // 2. Dense vector similarity search via pgvector
        try {
            String queryVector = embeddingService.generateEmbeddingVector(query);
            List<KbChunkRepository.ChunkSearchResultProjection> vectorResults =
                    chunkRepository.searchSimilarChunks(tenantId, queryVector, topK);
            for (var res : vectorResults) {
                double score = res.getSimilarityScore() != null ? res.getSimilarityScore() : 0.85;
                combined.putIfAbsent(res.getId(), new RetrievedChunk(
                        res.getId(),
                        res.getArticleId(),
                        res.getArticleTitle(),
                        res.getChunkIndex(),
                        res.getContent(),
                        score,
                        String.format("[%s - Chunk %d]", res.getArticleTitle(), res.getChunkIndex())
                ));
            }
        } catch (Exception e) {
            log.warn("Vector search failed for query '{}': {}", query, e.getMessage());
        }

        List<RetrievedChunk> results = new ArrayList<>(combined.values());
        results.sort((a, b) -> Double.compare(b.score(), a.score()));

        if (results.size() > topK) {
            return results.subList(0, topK);
        }
        return results;
    }
}
