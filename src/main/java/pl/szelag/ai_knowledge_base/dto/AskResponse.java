package pl.szelag.ai_knowledge_base.dto;

import java.util.List;

/**
 * Final response containing the AI-generated answer and the collection of
 * source document segments used to produce it.
 */
public record AskResponse(
        String answer,
        List<DocumentDto> relatedDocs) {

    /**
     * Returns a fallback response when no relevant context was found or the service
     * is unavailable.
     */
    public static AskResponse fallback(String message) {
        return new AskResponse(message, List.of());
    }
}