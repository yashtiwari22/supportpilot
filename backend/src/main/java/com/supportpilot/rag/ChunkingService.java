package com.supportpilot.rag;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for splitting knowledge base articles into semantic chunks with overlap.
 */
@Service
public class ChunkingService {

    private static final int DEFAULT_CHUNK_SIZE_WORDS = 250;
    private static final int DEFAULT_CHUNK_OVERLAP_WORDS = 40;

    public record TextChunk(int index, String content) {}

    /**
     * Splits source markdown/text into overlapping chunks.
     */
    public List<TextChunk> chunkText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String[] paragraphs = text.split("\\n\\s*\\n");
        List<String> words = new ArrayList<>();
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (!trimmed.isEmpty()) {
                String[] pWords = trimmed.split("\\s+");
                for (String w : pWords) {
                    if (!w.isBlank()) words.add(w);
                }
            }
        }

        if (words.size() <= DEFAULT_CHUNK_SIZE_WORDS) {
            return List.of(new TextChunk(0, String.join(" ", words)));
        }

        List<TextChunk> chunks = new ArrayList<>();
        int start = 0;
        int chunkIndex = 0;

        while (start < words.size()) {
            int end = Math.min(start + DEFAULT_CHUNK_SIZE_WORDS, words.size());
            List<String> slice = words.subList(start, end);
            chunks.add(new TextChunk(chunkIndex++, String.join(" ", slice)));

            if (end == words.size()) {
                break;
            }
            start += (DEFAULT_CHUNK_SIZE_WORDS - DEFAULT_CHUNK_OVERLAP_WORDS);
        }

        return chunks;
    }
}
