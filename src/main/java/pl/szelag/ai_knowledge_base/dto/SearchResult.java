package pl.szelag.ai_knowledge_base.dto;

import java.util.List;

/**
 * Internal container holding the intermediate results of the RAG retrieval
 * phase.
 * {@code context} is {@code null} when no documents passed the similarity
 * threshold.
 */
public record SearchResult(
        String context,
        List<DocumentDto> relatedDocs) {
}