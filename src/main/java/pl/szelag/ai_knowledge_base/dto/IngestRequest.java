package pl.szelag.ai_knowledge_base.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload containing raw text data to be processed, embedded, and stored in the
 * vector database.
 */
public record IngestRequest(
        @NotBlank String text) {
}