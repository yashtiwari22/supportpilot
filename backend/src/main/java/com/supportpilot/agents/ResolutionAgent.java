package com.supportpilot.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportpilot.llm.LlmClient;
import com.supportpilot.llm.LlmRequest;
import com.supportpilot.llm.LlmResponse;
import com.supportpilot.rag.HybridSearchService;
import com.supportpilot.ticket.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * AI Agent performing hybrid RAG retrieval over tenant help articles to draft a cited response.
 */
@Component
public class ResolutionAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(ResolutionAgent.class);

    private final LlmClient llmClient;
    private final HybridSearchService searchService;
    private final ObjectMapper objectMapper;
    private final String promptTemplate;

    public record ResolutionResult(
            String draftReply,
            List<String> citations,
            double confidence,
            List<HybridSearchService.RetrievedChunk> retrievedChunks
    ) {}

    public ResolutionAgent(LlmClient llmClient, HybridSearchService searchService, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.searchService = searchService;
        this.objectMapper = objectMapper;
        this.promptTemplate = loadPromptTemplate();
    }

    private String loadPromptTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/resolution_v1.txt");
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("Could not load prompts/resolution_v1.txt, using fallback template", e);
            return "Draft customer support reply using sources:\nTicket: {subject} - {body}\nSources:\n{knowledgeBaseSources}";
        }
    }

    @Override
    public AgentName name() {
        return AgentName.RESOLUTION;
    }

    @Override
    public AgentResult run(AgentContext context) {
        Ticket ticket = context.ticket();
        long startTime = System.currentTimeMillis();

        // 1. Hybrid RAG retrieval
        String query = ticket.getSubject() + " " + ticket.getBody();
        List<HybridSearchService.RetrievedChunk> retrieved = searchService.search(ticket.getTenantId(), query, 3);

        StringBuilder sourcesText = new StringBuilder();
        if (retrieved.isEmpty()) {
            sourcesText.append("No directly matching knowledge base articles found.");
        } else {
            for (int i = 0; i < retrieved.size(); i++) {
                var chunk = retrieved.get(i);
                sourcesText.append(String.format("[%d] %s (Score: %.2f):\n%s\n\n",
                        i + 1, chunk.citationReference(), chunk.score(), chunk.content()));
            }
        }

        String userPrompt = promptTemplate
                .replace("{customerName}", ticket.getCustomerName())
                .replace("{customerEmail}", ticket.getCustomerEmail())
                .replace("{subject}", ticket.getSubject())
                .replace("{body}", ticket.getBody())
                .replace("{knowledgeBaseSources}", sourcesText.toString());

        String systemPrompt = "You are ResolutionAgent. Draft an empathetic, helpful response with explicit citations [1], [2]. Output strictly JSON.";
        LlmResponse llmResponse = llmClient.chat(new LlmRequest(systemPrompt, userPrompt));

        ResolutionResult result = parseResolutionResult(llmResponse.content(), retrieved);
        long durationMs = System.currentTimeMillis() - startTime;

        return new AgentResult(
                name(),
                true,
                llmResponse.model(),
                llmResponse.promptTokens(),
                llmResponse.completionTokens(),
                llmResponse.estimatedCostUsd(),
                durationMs,
                result,
                null,
                Map.of(
                        "citationsCount", result.citations().size(),
                        "confidence", result.confidence()
                )
        );
    }

    private ResolutionResult parseResolutionResult(String rawJson, List<HybridSearchService.RetrievedChunk> chunks) {
        try {
            String clean = rawJson.trim();
            if (clean.startsWith("```json")) clean = clean.substring(7);
            else if (clean.startsWith("```")) clean = clean.substring(3);
            if (clean.endsWith("```")) clean = clean.substring(0, clean.length() - 3);

            var tree = objectMapper.readTree(clean.trim());
            String draft = tree.has("draftReply") ? tree.get("draftReply").asText() : "";
            double conf = tree.has("confidence") ? tree.get("confidence").asDouble() : 0.90;
            List<String> citations = chunks.stream().map(HybridSearchService.RetrievedChunk::citationReference).toList();

            return new ResolutionResult(draft, citations, conf, chunks);
        } catch (Exception e) {
            log.warn("Failed to parse resolution JSON, falling back: {}", e.getMessage());
            String defaultReply = "Thank you for reaching out. We have received your inquiry and our support team is actively reviewing your request.";
            List<String> citations = chunks.stream().map(HybridSearchService.RetrievedChunk::citationReference).toList();
            return new ResolutionResult(defaultReply, citations, 0.85, chunks);
        }
    }
}
