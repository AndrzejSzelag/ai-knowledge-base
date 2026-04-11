package pl.szelag.ai_knowledge_base.dto;

import java.util.Map;

/**
 * Represents a specific document fragment retrieved from the vector store,
 * including its content and associated metadata.
 */
public record DocumentDto(
                String documentId,
                String content,
                Map<String, Object> metadata,
                String similarityScore) {
}