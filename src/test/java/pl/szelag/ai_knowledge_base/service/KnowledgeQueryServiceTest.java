package pl.szelag.ai_knowledge_base.service;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import pl.szelag.ai_knowledge_base.config.RagProperties;
import pl.szelag.ai_knowledge_base.dto.AskResponse;
import pl.szelag.ai_knowledge_base.dto.SearchResult;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class KnowledgeQueryServiceTest {

    @Mock private VectorStore vectorStore;
    @Mock private ChatModel chatModel;
    @Mock private RagProperties ragProperties;

    @InjectMocks
    private KnowledgeQueryService knowledgeQueryService;

    // ── warmup() ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("warmup — completes without throwing even when vector store returns no results")
    void warmup_alwaysCompletes_withoutException() {
        // GIVEN — vector store returns empty list (threshold=0.0 bypasses filtering)
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        // WHEN & THEN — must never throw, LLM is never involved
        assertThatCode(() -> knowledgeQueryService.warmup())
                .doesNotThrowAnyException();
        verifyNoInteractions(chatModel);
    }

    // ── ask() ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ask — returns answer and related docs when context and LLM response are present")
    void ask_validQuestion_returnsResponseWithDocs() {
        // GIVEN
        String question = "How to configure HNSW?";
        String aiAnswer = "To configure HNSW, set m and ef_construction parameters.";
        mockRagProperties(3, 0.7, 2, 2000);

        Document doc = new Document("id-1", "HNSW config details...", Map.of("source", "manual"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        mockChatResponse(aiAnswer);

        // WHEN
        AskResponse response = knowledgeQueryService.ask(question);

        // THEN
        assertThat(response.answer()).isEqualTo(aiAnswer);
        assertThat(response.relatedDocs()).hasSize(1);
        assertThat(response.relatedDocs().get(0).documentId()).isEqualTo("id-1");
    }

    @Test
    @DisplayName("ask — returns fallback and skips LLM when no documents pass similarity threshold")
    void ask_noDocumentsFound_returnsFallbackWithoutCallingLlm() {
        // GIVEN
        when(ragProperties.getTopK()).thenReturn(3);
        when(ragProperties.getSimilarityThreshold()).thenReturn(0.7);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        // WHEN
        AskResponse response = knowledgeQueryService.ask("Non-existent topic");

        // THEN
        assertThat(response.answer()).contains("I don't have information about that");
        verifyNoInteractions(chatModel);
    }

    @Test
    @DisplayName("ask — throws NullPointerException when ChatModel returns a response with no generations")
    void ask_emptyGenerationList_throwsNullPointerException() {
        // GIVEN — ChatResponse with empty generation list causes getResult() to return null.
        // This is a known unguarded path — the service does not null-check the generation result.
        mockRagProperties(1, 0.7, 1, 500);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("id", "some content", Map.of())));
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));

        // WHEN & THEN
        assertThatThrownBy(() -> knowledgeQueryService.ask("test"))
                .isInstanceOf(NullPointerException.class);
    }

    // ── performSearch() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("performSearch — returns null context and empty doc list when no documents match")
    void performSearch_noDocuments_returnsNullContext() {
        // GIVEN
        when(ragProperties.getTopK()).thenReturn(3);
        when(ragProperties.getSimilarityThreshold()).thenReturn(0.7);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        // WHEN
        SearchResult result = knowledgeQueryService.performSearch("unknown topic");

        // THEN
        assertThat(result.context()).isNull();
        assertThat(result.relatedDocs()).isEmpty();
    }

    @Test
    @DisplayName("performSearch — context contains document label, source and content")
    void performSearch_validDocument_buildsCorrectContext() {
        // GIVEN
        mockRagProperties(1, 0.7, 1, 500);
        Document doc = new Document("id", "Semantic Memory explained", Map.of("source", "wiki"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        // WHEN
        SearchResult result = knowledgeQueryService.performSearch("memory");

        // THEN
        assertThat(result.context())
                .contains("[Doc 1 | source=wiki]:")
                .contains("Semantic Memory explained");
        assertThat(result.relatedDocs()).hasSize(1);
    }

    @Test
    @DisplayName("performSearch — global truncation applied when combined context exceeds maxContextChars")
    void performSearch_longContext_truncatesToGlobalLimit() {
        // GIVEN — prefix "[Doc 1 | source=db]: " is ~22 chars, so limit must be > 22 to have any content
        mockRagProperties(1, 0.7, 1, 50);
        Document doc = new Document("id", "A".repeat(200), Map.of("source", "db"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        // WHEN
        SearchResult result = knowledgeQueryService.performSearch("test");

        // THEN
        assertThat(result.context().length()).isLessThanOrEqualTo(50);
        assertThat(result.context()).startsWith("[Doc 1 | source=db]:");
    }

    @Test
    @DisplayName("performSearch — only first N docs injected into context when more than finalContextDocs returned")
    void performSearch_moreDocsThanFinalContextDocs_onlyFirstInjected() {
        // GIVEN — 3 docs returned but finalContextDocs = 1
        mockRagProperties(3, 0.7, 1, 2000);
        List<Document> docs = List.of(
                new Document("id-1", "First doc", Map.of("source", "a")),
                new Document("id-2", "Second doc", Map.of("source", "b")),
                new Document("id-3", "Third doc", Map.of("source", "c")));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(docs);

        // WHEN
        SearchResult result = knowledgeQueryService.performSearch("query");

        // THEN — context limited to first doc; all 3 DTOs still returned as references
        assertThat(result.context())
                .contains("First doc")
                .doesNotContain("Second doc")
                .doesNotContain("Third doc");
        assertThat(result.relatedDocs()).hasSize(3);
    }

    @Test
    @DisplayName("performSearch — text truncated with '...' at last sentence boundary when too long")
    void performSearch_longDocText_truncatedAtSentenceBoundary() {
        // GIVEN — text has a sentence boundary at char 200, followed by 400 more chars
        String longText = "A".repeat(200) + ". " + "B".repeat(400);
        Document doc = new Document(longText, Map.of("source", "test.pdf"));
        mockRagProperties(1, 0.7, 1, 300);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        // WHEN
        SearchResult result = knowledgeQueryService.performSearch("question");

        // THEN — truncation appends "..."
        assertThat(result.context()).endsWith("...");
    }

    @Test
    @DisplayName("performSearch — text without sentence boundary is hard-truncated at perDocLimit")
    void performSearch_longDocTextNoPeriod_hardTruncatedAtLimit() {
        // GIVEN — no period in text, so lastIndexOf('.') returns -1 and cut falls back to perDocLimit
        String longText = "A".repeat(600);
        Document doc = new Document(longText, Map.of("source", "test.pdf"));
        mockRagProperties(1, 0.7, 1, 2000);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        // WHEN
        SearchResult result = knowledgeQueryService.performSearch("question");

        // THEN
        assertThat(result.context())
                .isNotNull()
                .contains("source=test.pdf")
                .contains("A".repeat(100));
    }

    @Test
    @DisplayName("performSearch — global hard truncation fires when combined context exceeds maxChars")
    void performSearch_combinedContextExceedsMax_globalTruncation() {
        // GIVEN — tiny limit of 10 chars; even a single doc label exceeds it, triggering global cut
        mockRagProperties(2, 0.7, 2, 10);
        Document doc1 = new Document("Short text.", Map.of("source", "a.pdf"));
        Document doc2 = new Document("Short text.", Map.of("source", "b.pdf"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc1, doc2));

        // WHEN
        SearchResult result = knowledgeQueryService.performSearch("question");

        // THEN
        assertThat(result.context().length()).isLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("performSearch — missing 'source' key in metadata falls back to 'unknown'")
    void performSearch_missingSourceMetadata_usesUnknownSource() {
        // GIVEN — metadata exists but contains no 'source' key; getOrDefault returns "unknown"
        Document doc = new Document("Some content", Map.of());
        mockRagProperties(1, 0.7, 1, 1000);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        // WHEN
        SearchResult result = knowledgeQueryService.performSearch("question");

        // THEN
        assertThat(result.context()).contains("source=unknown");
    }

    // ── streamAnswer() ────────────────────────────────────────────────────────

    @Test
    @DisplayName("streamAnswer — null getText() is treated as empty string by extractText()")
    void streamAnswer_nullTextFromResponse_emitsEmptyString() {
        // GIVEN — extractText() guards against null getText() and returns ""
        // The empty string IS emitted (no filter in the stream pipeline)
        ChatResponse mockResponse = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(mockResponse.getResult().getOutput().getText()).thenReturn(null);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(mockResponse));

        // WHEN
        List<String> tokens = knowledgeQueryService
                .streamAnswer("context", "question", 100L)
                .collectList()
                .block();

        // THEN — one empty string token emitted, not filtered out
        assertThat(tokens)
                .hasSize(1)
                .containsExactly("");
    }

    // ── ask() — preview edge case ─────────────────────────────────────────────

    @Test
    @DisplayName("ask — answer longer than 80 chars does not cause an exception in preview()")
    void ask_longAnswer_previewDoesNotThrow() {
        // GIVEN
        mockRagProperties(1, 0.7, 1, 1000);
        String longAnswer = "X".repeat(200);
        ChatResponse mockResponse = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(mockResponse.getResult().getOutput().getText()).thenReturn(longAnswer);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("some content", Map.of())));

        // WHEN & THEN
        assertThatCode(() -> knowledgeQueryService.ask("question"))
                .doesNotThrowAnyException();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void mockRagProperties(int topK, double threshold, int finalDocs, int maxChars) {
        when(ragProperties.getTopK()).thenReturn(topK);
        when(ragProperties.getSimilarityThreshold()).thenReturn(threshold);
        when(ragProperties.getFinalContextDocs()).thenReturn(finalDocs);
        when(ragProperties.getMaxContextChars()).thenReturn(maxChars);
    }

    private void mockChatResponse(String answer) {
        AssistantMessage msg = new AssistantMessage(answer);
        ChatResponse response = new ChatResponse(List.of(new Generation(msg)));
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }
}