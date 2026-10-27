package com.supportpilot.rag;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Random;

/**
 * Port interface and mock implementation for generating vector embeddings.
 */
public interface EmbeddingService {

    int EMBEDDING_DIMENSION = 1536;

    /**
     * Generates a normalized 1536-dimensional vector formatted as a string for pgvector '[x1,x2,...]'.
     */
    String generateEmbeddingVector(String text);

    /**
     * Mock implementation producing deterministic normalized vectors for testing and $0 local execution.
     */
    @Service
    class MockEmbeddingService implements EmbeddingService {

        @Override
        public String generateEmbeddingVector(String text) {
            float[] vector = new float[EMBEDDING_DIMENSION];
            long seed = hashSeed(text);
            Random random = new Random(seed);

            float sumSq = 0.0f;
            for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
                vector[i] = (random.nextFloat() * 2.0f) - 1.0f;
                sumSq += vector[i] * vector[i];
            }

            // Normalize vector to unit length (L2 norm)
            float norm = (float) Math.sqrt(sumSq);
            if (norm > 0) {
                for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
                    vector[i] /= norm;
                }
            }

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
                sb.append(String.format(java.util.Locale.US, "%.6f", vector[i]));
                if (i < EMBEDDING_DIMENSION - 1) sb.append(",");
            }
            sb.append("]");
            return sb.toString();
        }

        private long hashSeed(String text) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] bytes = md.digest(text.toLowerCase().getBytes(StandardCharsets.UTF_8));
                long seed = 0;
                for (int i = 0; i < 8; i++) {
                    seed = (seed << 8) | (bytes[i] & 0xff);
                }
                return seed;
            } catch (Exception e) {
                return text.hashCode();
            }
        }
    }
}
