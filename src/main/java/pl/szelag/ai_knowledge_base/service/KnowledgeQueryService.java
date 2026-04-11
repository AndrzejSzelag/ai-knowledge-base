package pl.szelag.ai_knowledge_base.service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.szelag.ai_knowledge_base.config.AIWarmupRunner;
import pl.szelag.ai_knowledge_base.config.RagProperties;
import pl.szelag.ai_knowledge_base.dto.AskResponse;
import pl.szelag.ai_knowledge_base.dto.DocumentDto;
import pl.szelag.ai_knowledge_base.dto.SearchResult;
import reactor.core.publisher.Flux;

/** RAG pipeline: vector search → prompt assembly → LLM call. */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeQueryService {

    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final RagProperties ragProperties;

    // Must match Ollama num-ctx setting in application.yaml.
    private static final double CONTEXT_WINDOW_SIZE = 1536.0;

    private static final String METADATA_DISTANCE = "distance";
    private static final String METADATA_SOURCE = "source";

    // Minimal warm-up query; threshold 0.0 guarantees a DB hit regardless of
    // content.
    private static final String WARMUP_QUERY = "warmup";

    /** System prompt enforcing strict context-only answers. */
    private static final String SYSTEM_PROMPT = """
            You are a strict Knowledge Base assistant.
            Answer the question using ONLY the provided Context.

            STRICT RULES:
            - Do NOT use any external knowledge.
            - Do NOT infer or guess missing information.
            - If the answer is not in the Context, respond EXACTLY:
              "I don't know based on the provided context."
            - Keep the answer short (max 2-3 sentences).
            - Use only facts clearly stated in the Context.
            """;

    /** User message template. Placeholders: 1st %s = context, 2nd %s = question. */
    private static final String USER_PROMPT_TEMPLATE = """
            CONTEXT:
            %s

            QUESTION:
            %s

            ANSWER:
            """;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Warms up the vector store without invoking the LLM.
     * Forces JDBC pool init and pgvector index loading into RAM.
     * Called once at startup via {@link AIWarmupRunner}.
     */
    public void warmup() {
        log.info("Running vector store warm-up (skipping LLM call)...");
        vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(WARMUP_QUERY)
                        .topK(1)
                        .similarityThreshold(0.0)
                        .build());
        log.info("Vector store warm-up done.");
    }

    /**
     * Synchronous RAG query — vector search + LLM call.
     * Returns fallback answer if no documents pass the similarity threshold.
     */
    public AskResponse ask(String question) {
        // nanoTime() = monotonic clock, immune to NTP/system clock changes.
        long startNano = System.nanoTime();

        SearchResult searchResult = performSearch(question);
        long searchDuration = toMs(startNano);

        if (searchResult.context() == null) {
            log.warn("No relevant context found for: '{}'", question);
            return AskResponse.fallback("I don't have information about that in my database.");
        }

        ChatResponse response = chatModel.call(buildPrompt(searchResult.context(), question));
        logUsage(response, searchDuration);

        String answer = response.getResult().getOutput().getText();
        log.info("Synchronous answer generated. Preview: {}", preview(answer));
        return new AskResponse(answer, searchResult.relatedDocs());
    }

    /**
     * Streams LLM response tokens as a reactive {@link Flux}.
     *
     * @param context      context string from {@link #performSearch}
     * @param question     original user question
     * @param searchTimeMs vector search latency from the controller
     */
    public Flux<String> streamAnswer(String context, String question, long searchTimeMs) {
        StringBuilder fullResponse = new StringBuilder();

        return chatModel.stream(buildPrompt(context, question))
                .map(resp -> {
                    String text = extractText(resp);
                    fullResponse.append(text);

                    // Log token usage only on the final chunk (when usage data is populated).
                    if (resp.getMetadata() != null && resp.getMetadata().getUsage() != null
                            && resp.getMetadata().getUsage().getTotalTokens() > 0) {
                        logUsage(resp, searchTimeMs);
                    }
                    return text;
                })
                .doOnComplete(() -> log.info("Stream completed. Preview: {}",
                        preview(fullResponse.toString())));
    }

    /**
     * Runs vector similarity search and returns context + matched document DTOs.
     * Returns {@code null} context if no documents pass the threshold.
     */
    public SearchResult performSearch(String question) {
        long startNano = System.nanoTime();

        double threshold = ragProperties.getSimilarityThreshold();
        int topK = ragProperties.getTopK();

        log.info("=== START VECTOR SEARCH ===");
        log.info("Query: '{}' | Threshold: {} | TopK: {}", question, threshold, topK);

        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(topK)
                        .similarityThreshold(threshold)
                        .build());

        log.info("Vector search took {} ms", toMs(startNano));

        if (documents.isEmpty()) {
            log.warn("SEARCH FAILURE: No documents passed threshold ({})", threshold);
            return new SearchResult(null, List.of());
        }

        List<DocumentDto> dtos = documents.stream().map(this::mapToDocumentDto).toList();

        log.info("=== SEARCH SUCCESS: {} documents found ===", dtos.size());
        dtos.forEach(dto -> log.info(" -> [Score: {}] ID: {} | Source: {}",
                dto.similarityScore(), dto.documentId(),
                dto.metadata().getOrDefault(METADATA_SOURCE, "unknown")));

        String context = buildContext(documents);
        log.debug("Context: {} docs, {} chars", documents.size(), context.length());

        return new SearchResult(context, dtos);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Converts a {@code nanoTime()} start to elapsed milliseconds. */
    private static long toMs(long startNano) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
    }

    /**
     * Maps a Spring AI {@link Document} to {@link DocumentDto}.
     * PGVector stores cosine distance (0=identical); converts to similarity = 1 -
     * distance.
     */
    private DocumentDto mapToDocumentDto(Document doc) {
        double similarity = 1.0;
        Object raw = doc.getMetadata().getOrDefault(METADATA_DISTANCE, null);
        if (raw != null) {
            try {
                similarity = 1.0 - Double.parseDouble(String.valueOf(raw));
            } catch (NumberFormatException ignored) {
            }
        }
        return new DocumentDto(doc.getId(), doc.getText(), doc.getMetadata(),
                String.format("%.4f", similarity));
    }

    /** Assembles the {@link Prompt} from system instructions and user message. */
    private Prompt buildPrompt(String context, String question) {
        return new Prompt(List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(USER_PROMPT_TEMPLATE.formatted(context, question))));
    }

    /** Safely extracts text from a partial or null {@link ChatResponse}. */
    private String extractText(ChatResponse resp) {
        if (resp == null || resp.getResult() == null || resp.getResult().getOutput() == null)
            return "";
        return Objects.requireNonNullElse(resp.getResult().getOutput().getText(), "");
    }

    /**
     * Builds context from retrieved documents.
     * Distributes character budget evenly across docs; truncates at sentence
     * boundary.
     * Hard-truncates if total still exceeds {@code maxContextChars}.
     */
    private String buildContext(List<Document> docs) {
        int maxDocs = Math.min(docs.size(), ragProperties.getFinalContextDocs());
        int maxChars = ragProperties.getMaxContextChars();
        int perDocLimit = maxChars / Math.max(1, maxDocs);

        String context = IntStream.range(0, maxDocs)
                .mapToObj(i -> {
                    Document doc = docs.get(i);
                    String text = doc.getText() != null ? doc.getText() : "";
                    String source = (doc.getMetadata() != null)
                            ? String.valueOf(doc.getMetadata().getOrDefault(METADATA_SOURCE, "unknown"))
                            : "unknown";

                    if (text.length() > perDocLimit) {
                        int cut = text.lastIndexOf('.', perDocLimit);
                        if (cut == -1)
                            cut = perDocLimit;
                        text = text.substring(0, cut) + "...";
                    }
                    return "[Doc %d | source=%s]: %s".formatted(i + 1, source, text);
                })
                .collect(Collectors.joining("\n\n---\n\n"));

        if (context.length() > ragProperties.getMaxContextChars() * 0.9) {
            log.warn("Context fill at {}% ({} chars) — approaching token limit.",
                    (context.length() * 100 / ragProperties.getMaxContextChars()), context.length());
        }

        if (context.length() > maxChars) {
            log.warn("Context hard-truncated to {} chars.", maxChars);
            context = context.substring(0, maxChars);
        }

        return context;
    }

    /** Logs token usage and latency for a completed RAG request. */
    private void logUsage(ChatResponse response, long searchTimeMs) {
        if (response == null || response.getMetadata() == null
                || response.getMetadata().getUsage() == null)
            return;

        var usage = response.getMetadata().getUsage();
        if (usage.getTotalTokens() <= 0)
            return;

        double contextLimit = CONTEXT_WINDOW_SIZE; // must match num-ctx in application.yaml
        double fillPercent = (usage.getTotalTokens() / contextLimit) * 100;

        log.info("============================================================");
        log.info("========= RAG REPORT =========");
        log.info("1. LATENCY  -> Vector Search: {} ms", searchTimeMs);
        log.info("2. TOKENS   (num-ctx: {}):", (int) contextLimit);
        log.info("   -> Prompt:     {}", usage.getPromptTokens());
        log.info("   -> Generation: {}", usage.getGenerationTokens());
        log.info("   -> Total:      {}", usage.getTotalTokens());
        log.info("   -> Fill:       {}%", String.format("%.2f", fillPercent));
        log.info("============================================================");

        if (fillPercent > 90)
            log.warn("!!! ALERT: Context window usage exceeds 90%!");
    }

    /** Returns a single-line preview of text for logging (max 80 chars). */
    private String preview(String text) {
        if (text == null || text.isBlank())
            return "";
        return text.substring(0, Math.min(text.length(), 80)).replace("\n", " ");
    }
}