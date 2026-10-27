package com.supportpilot.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkingServiceTest {

    private final ChunkingService chunkingService = new ChunkingService();

    @Test
    @DisplayName("Short text fits into a single chunk")
    void shortTextReturnsSingleChunk() {
        String text = "This is a brief return policy article explaining standard 30-day returns.";
        List<ChunkingService.TextChunk> chunks = chunkingService.chunkText(text);

        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).index());
        assertTrue(chunks.get(0).content().contains("30-day returns"));
    }

    @Test
    @DisplayName("Long text produces multiple overlapping chunks")
    void longTextProducesOverlappingChunks() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 600; i++) {
            sb.append("word").append(i).append(" ");
        }

        List<ChunkingService.TextChunk> chunks = chunkingService.chunkText(sb.toString());

        assertTrue(chunks.size() >= 2);
        assertEquals(0, chunks.get(0).index());
        assertEquals(1, chunks.get(1).index());
        // Verify overlap: end of chunk 0 should overlap with start of chunk 1
        String[] c0Words = chunks.get(0).content().split("\\s+");
        String[] c1Words = chunks.get(1).content().split("\\s+");
        assertEquals(c0Words[c0Words.length - 1], c1Words[39]);
    }

    @Test
    @DisplayName("Empty or null text produces empty list")
    void emptyOrNullReturnsEmpty() {
        assertTrue(chunkingService.chunkText(null).isEmpty());
        assertTrue(chunkingService.chunkText("   ").isEmpty());
    }
}
