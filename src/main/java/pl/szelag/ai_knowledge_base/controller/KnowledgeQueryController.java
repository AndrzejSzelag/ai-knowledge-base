package pl.szelag.ai_knowledge_base.controller;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.szelag.ai_knowledge_base.dto.AskRequest;
import pl.szelag.ai_knowledge_base.dto.AskResponse;
import pl.szelag.ai_knowledge_base.dto.SearchResult;
import pl.szelag.ai_knowledge_base.service.KnowledgeQueryService;
import reactor.core.publisher.Flux;

/** Handles RAG-based question answering over the AI Knowledge Base. */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Validated
public class KnowledgeQueryController {

    private final KnowledgeQueryService knowledgeQueryService;

    /**
     * Synchronous RAG endpoint.
     * Performs vector search + LLM call and returns the full answer with source
     * references.
     * Use this for simple clients that do not support SSE streaming.
     */
    @PostMapping("/ask")
    public AskResponse ask(@RequestBody @Valid AskRequest request) {
        log.info("AI question received (Standard): {}", request.question());
        return knowledgeQueryService.ask(request.question());
    }

    /**
     * Streaming RAG endpoint.
     * Performs vector search first, then streams LLM tokens as SSE "message"
     * events.
     * Ends with a single "references" event containing the source documents.
     *
     * Event types emitted:
     * - "message" — individual LLM token chunks
     * - "references" — List<DocumentDto> with matched source documents
     * - "error" — error description if LLM generation fails
     */
    @PostMapping(value = "/ask-streaming", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> askStreaming(@RequestBody @Valid AskRequest request) {
        log.info("AI question received (Streaming): {}", request.question());

        // Track vector search latency
        long startTime = System.currentTimeMillis();
        // Run vector similarity search before opening the stream
        SearchResult searchResult = knowledgeQueryService.performSearch(request.question());

        long searchTimeMs = System.currentTimeMillis() - startTime;

        // No relevant documents found — return a single informational event without
        // calling LLM
        if (searchResult.context() == null) {
            log.warn("No relevant context found for: '{}'. Similarity threshold blocked LLM call.",
                    request.question());
            return Flux.just(noContextEvent());
        }

        // Stream LLM tokens — pass raw context string and search latency for the RAG
        // report
        Flux<ServerSentEvent<Object>> messageFlux = knowledgeQueryService
                .streamAnswer(searchResult.context(), request.question(), searchTimeMs)
                .map(this::toMessageEvent)
                .onErrorResume(e -> {
                    log.error("Streaming error: {}", e.getMessage(), e);
                    return Flux.just(toErrorEvent("Error during AI generation."));
                });

        // Emit source document references as the final SSE event after streaming
        // completes
        // Wrap references in Flux.defer so they are sent last
        Flux<ServerSentEvent<Object>> referencesFlux = Flux.just(
                ServerSentEvent.builder()
                        .event("references")
                        .data((Object) searchResult.relatedDocs())
                        .build());

        // Concatenate: stream tokens first (message), then source references
        return Flux.concat(messageFlux, referencesFlux);
    }

    // ── SSE builders ──────────────────────────────────────────────────────────

    /** Wraps a single LLM token as an SSE "message" event. */
    private ServerSentEvent<Object> toMessageEvent(String token) {
        return ServerSentEvent.builder()
                .data((Object) token)
                .build();
    }

    /** Wraps an error message as an SSE "error" event. */
    private ServerSentEvent<Object> toErrorEvent(String message) {
        return ServerSentEvent.builder()
                .event("error")
                .data((Object) message)
                .build();
    }

    /** Returned when no relevant context was found — skips LLM entirely. */
    private ServerSentEvent<Object> noContextEvent() {
        return ServerSentEvent.builder()
                .event("message")
                .data((Object) "I'm sorry, I don't have enough information in my database to answer this question.")
                .build();
    }
}