package pl.szelag.ai_knowledge_base.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Data transfer object representing the user's natural language query submitted
 * to the RAG pipeline.
 */
public record AskRequest(
                @NotBlank String question) {
}