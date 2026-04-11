package pl.szelag.ai_knowledge_base.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.szelag.ai_knowledge_base.config.SplitterProperties;
import pl.szelag.ai_knowledge_base.dto.DocumentDto;
import pl.szelag.ai_knowledge_base.repository.DocumentProjection;
import pl.szelag.ai_knowledge_base.repository.VectorStoreRepository;

/**
 * Manages ingestion, retrieval, and deletion of documents in the vector store.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIngestService {

    private static final String METADATA_SOURCE = "source";
    private static final String METADATA_CHUNK_INDEX = "chunk_index";

    private final SplitterProperties splitter;
    private final VectorStore vectorStore;
    private final VectorStoreRepository vectorStoreRepository;

    // Handles destructive admin operations (truncate, deleteBySource with guard).
    private final VectorStoreAdminService vectorStoreAdminService;

    // Self-injection via @Lazy proxy to ensure @CacheEvict fires on internal calls
    // (Spring AOP intercepts only external method invocations).
    @Autowired
    @Lazy
    private KnowledgeIngestService self;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Splits {@code text} into chunks and stores them in the vector store.
     * Not {@code @Transactional} — avoids holding a DB connection during
     * the Ollama embedding call, which may take several seconds.
     */
    @CacheEvict(value = { "documentCount", "documents" }, allEntries = true)
    public void ingest(String text, Map<String, Object> additionalMetadata) {
        if (text == null || text.isBlank())
            return;

        String source = String.valueOf(additionalMetadata.getOrDefault(METADATA_SOURCE, "manual_upload"));

        Map<String, Object> metadata = new HashMap<>(additionalMetadata);
        metadata.put(METADATA_SOURCE, source);
        metadata.put("ingested_at", System.currentTimeMillis());

        List<Document> chunks = splitDocument(new Document(text, metadata));
        enrichChunkMetadata(chunks);

        try {
            log.info("Sending {} chunks to Ollama for embedding (source: {})...", chunks.size(), source);
            vectorStore.add(chunks);
            log.info("SUCCESS: Ingested source '{}'.", source);
        } catch (Exception e) {
            log.error("CRITICAL: VectorStore.add failed — {}", e.getMessage());
            throw new RuntimeException("Vector store ingestion failed", e);
        }
    }

    /**
     * Convenience overload — routes through Spring proxy to honour
     * {@code @CacheEvict}.
     */
    public void ingest(String text) {
        self.ingest(text, Map.of());
    }

    /** Deletes a single document by ID and evicts caches. */
    @Transactional
    @CacheEvict(value = { "documentCount", "documents" }, allEntries = true)
    public void deleteDocument(String documentId) {
        vectorStore.delete(List.of(documentId));
        log.info("Deleted vector entry: {}", documentId);
    }

    /**
     * Removes all documents from the vector store and evicts caches.
     * Delegates to {@link VectorStoreAdminService#truncateAll()} which owns
     * the destructive operation with proper logging and transaction management.
     */
    @CacheEvict(value = { "documentCount", "documents" }, allEntries = true)
    public void deleteAllDocuments() {
        vectorStoreAdminService.truncateAll();
    }

    // Caching intentionally disabled — pagination keys are not stable enough yet.
    public Page<DocumentDto> getDocuments(Pageable pageable) {
        try {
            Page<DocumentProjection> page = vectorStoreRepository.findAllSimplified(pageable);

            List<DocumentDto> dtos = page.getContent().stream()
                    .map(p -> new DocumentDto(
                            String.valueOf(p.getId()),
                            p.getContent(),
                            Map.of(METADATA_SOURCE, (Object) p.getSource()),
                            null))
                    .toList();

            return new PageImpl<>(dtos, pageable, page.getTotalElements());
        } catch (Exception e) {
            log.error("Failed to retrieve documents: {}", e.getMessage());
            return Page.empty(pageable);
        }
    }

    /**
     * Returns total document count. Zero is not cached to avoid stale empty state.
     */
    @Cacheable(value = "documentCount", unless = "#result == 0")
    public long getCount() {
        return vectorStoreRepository.count();
    }

    /**
     * Replaces all chunks for the given document with re-ingested {@code newText}.
     * Looks up the original source key, deletes existing chunks via
     * {@link VectorStoreAdminService#deleteBySource(String)} (validates non-blank),
     * then re-ingests.
     */
    @Transactional
    @CacheEvict(value = { "documentCount", "documents" }, allEntries = true)
    public void updateDocument(String documentId, String newText) {
        UUID uuid = UUID.fromString(documentId);
        String source = vectorStoreRepository.findSourceById(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        vectorStoreAdminService.deleteBySource(source);
        vectorStoreRepository.flush();
        ingest(newText, Map.of(METADATA_SOURCE, source));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Splits a document into token-based chunks.
     * Short texts below the threshold are returned as-is.
     */
    private List<Document> splitDocument(Document document) {
        if (document.getText().length() < splitter.getShortTextThreshold()) {
            return List.of(document);
        }

        List<Document> chunks = new TokenTextSplitter(
                splitter.getChunkSize(),
                splitter.getEffectiveChunkOverlap(),
                splitter.getMinChunkSize(),
                splitter.getMaxChunkSize(),
                true).split(List.of(document));

        // Debug: verify chunk overlap for splitter tuning.
        if (log.isDebugEnabled() && chunks.size() > 1) {
            String endOfFirst = chunks.get(0).getText();
            endOfFirst = endOfFirst.substring(Math.max(0, endOfFirst.length() - 60));
            String startOfSecond = chunks.get(1).getText().substring(0, Math.min(chunks.get(1).getText().length(), 60));

            log.debug("=== OVERLAP CHECK ===");
            log.debug("Chunk 1 ends:   '{}'", endOfFirst.replace("\n", " "));
            log.debug("Chunk 2 starts: '{}'", startOfSecond.replace("\n", " "));
            log.debug("=====================");
        }

        return chunks;
    }

    /** Tags each chunk with its index and total chunk count for traceability. */
    private void enrichChunkMetadata(List<Document> chunks) {
        int total = chunks.size();
        for (int i = 0; i < total; i++) {
            chunks.get(i).getMetadata().put(METADATA_CHUNK_INDEX, i);
            chunks.get(i).getMetadata().put("chunk_total", total);
        }
    }
}