package pl.szelag.ai_knowledge_base;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import pl.szelag.ai_knowledge_base.dto.AskRequest;
import pl.szelag.ai_knowledge_base.service.KnowledgeIngestService;
import pl.szelag.ai_knowledge_base.service.VectorStoreAdminService;

/**
 * Integration test for the AI Knowledge Base query engine.
 * Relies on the database configured in application-test-local.yml (port 5555).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test-local")
class KnowledgeQueryIT {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private VectorStoreAdminService vectorStoreAdminService;

    @Autowired
    private KnowledgeIngestService ingestService;

    @BeforeEach
    void setUp() {
        this.webTestClient = webTestClient.mutate()
                .responseTimeout(Duration.ofSeconds(60))
                .build();

        // Ensure clean, predictable state before each test
        vectorStoreAdminService.truncateAll();
        ingestService.ingest(
                "SQL (Structured Query Language) is used to manage relational databases.",
                Map.of("source", "sql-basics.txt", "category", "Databases"));
    }

    @Test
    @DisplayName("Should successfully retrieve context and stream response with references")
    void shouldReturnStreamingResponseForSqlQuestion() {
        // GIVEN
        AskRequest request = new AskRequest("What is SQL?");

        // WHEN
        var result = webTestClient.post()
                .uri("/api/ai/ask-streaming")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                });

        // THEN
        List<ServerSentEvent<String>> events = result.getResponseBody()
                .collectList()
                .block(Duration.ofSeconds(90));

        assertThat(events)
                .as("The event stream should not be empty")
                .isNotNull()
                .isNotEmpty();

        // Extract and validate response content
        String fullResponse = events.stream()
                .filter(e -> e.data() != null)
                .filter(e -> e.event() == null || "message".equals(e.event()))
                .map(ServerSentEvent::data)
                .collect(Collectors.joining(""));

        assertThat(fullResponse)
                .as("The reconstructed AI response should not be blank")
                .isNotBlank();

        assertThat(fullResponse)
                .as("The response should contain the keyword 'SQL'")
                .containsIgnoringCase("SQL");

        // Verify source references
        boolean hasReferences = events.stream()
                .anyMatch(e -> "references".equals(e.event()) && e.data() != null);

        assertThat(hasReferences)
                .as("The stream should contain source metadata (event: references)")
                .isTrue();
    }

    @Test
    @DisplayName("Should handle out-of-scope questions based on similarity threshold")
    void shouldHandleOutOfScopeQuestion() {
        // GIVEN
        AskRequest request = new AskRequest("How to make a pepperoni pizza?");

        // WHEN
        var result = webTestClient.post()
                .uri("/api/ai/ask-streaming")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                });

        // THEN
        List<ServerSentEvent<String>> events = result.getResponseBody()
                .collectList()
                .block(Duration.ofSeconds(60));

        assertThat(events).isNotEmpty();
        String rejectionMessage = events.get(0).data();
        assertThat(rejectionMessage).contains("I'm sorry, I don't have enough information");
    }
}