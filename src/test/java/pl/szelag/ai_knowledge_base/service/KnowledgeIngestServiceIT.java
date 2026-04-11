package pl.szelag.ai_knowledge_base.service;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import pl.szelag.ai_knowledge_base.repository.DocumentProjection;
import pl.szelag.ai_knowledge_base.repository.VectorStoreRepository;

/**
 * Integration tests for KnowledgeIngestService.
 * Requires a running pgvector container on port 5555.
 * Start with: ./test-it.sh
 */
@SpringBootTest
@ActiveProfiles("test-local")
@Transactional
class KnowledgeIngestServiceIT {

    @Autowired
    private KnowledgeIngestService knowledgeIngestService;

    @Autowired
    private VectorStoreRepository vectorStoreRepository;

    @Autowired
    private VectorStoreAdminService vectorStoreAdminService;

    @BeforeEach
    void setUp() {
        vectorStoreAdminService.truncateAll();
    }

    @Test
    @DisplayName("Should physically store and retrieve a document from PGVector")
    void ingest_savesDocumentToDatabase() {
        // GIVEN
        String content = "Spring AI provides a unified interface for multiple AI providers.";
        String source = "integration-test-file.txt";

        // WHEN
        knowledgeIngestService.ingest(content, Map.of("source", source));

        // THEN
        long count = knowledgeIngestService.getCount();
        assertThat(count).isEqualTo(1L);

        Page<DocumentProjection> page = vectorStoreRepository.findAllSimplified(PageRequest.of(0, 10));
        DocumentProjection doc = page.getContent().get(0);
        assertThat(doc.getContent()).isEqualTo(content);
    }

    @Test
    @DisplayName("Should replace existing document content when updateDocument is called")
    void updateDocument_replacesExistingSourceChunks() {
        // GIVEN — ingest initial version
        String content = "Old version content.";
        String source = "unique-doc-v1";
        knowledgeIngestService.ingest(content, Map.of("source", source));

        Page<DocumentProjection> before = vectorStoreRepository.findAllSimplified(PageRequest.of(0, 10));
        String docId = String.valueOf(before.getContent().get(0).getId());

        // WHEN — update replaces old chunks with new content
        knowledgeIngestService.updateDocument(docId, "New version content.");

        // THEN — still one document, but with updated content
        assertThat(knowledgeIngestService.getCount()).isEqualTo(1L);
        Page<DocumentProjection> after = vectorStoreRepository.findAllSimplified(PageRequest.of(0, 10));
        assertThat(after.getContent().get(0).getContent()).isEqualTo("New version content.");
    }

    @Test
    @DisplayName("Should correctly handle full store truncation via admin service")
    void deleteAllDocuments_clearsDatabasePhysically() {
        // GIVEN — two documents with distinct sources
        knowledgeIngestService.ingest("Doc 1", Map.of("source", "source_1"));
        knowledgeIngestService.ingest("Doc 2", Map.of("source", "source_2"));
        assertThat(knowledgeIngestService.getCount()).isEqualTo(2L);

        // WHEN
        knowledgeIngestService.deleteAllDocuments();

        // THEN
        assertThat(knowledgeIngestService.getCount()).isZero();
    }
}