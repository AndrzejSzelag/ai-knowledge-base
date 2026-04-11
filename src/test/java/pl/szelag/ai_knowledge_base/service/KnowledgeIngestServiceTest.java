package pl.szelag.ai_knowledge_base.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import pl.szelag.ai_knowledge_base.config.SplitterProperties;
import pl.szelag.ai_knowledge_base.repository.VectorStoreRepository;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestServiceTest {

    @Mock
    private VectorStore vectorStore;
    @Mock
    private VectorStoreRepository vectorStoreRepository;
    @Mock
    private SplitterProperties splitterProperties;
    @Mock
    private VectorStoreAdminService vectorStoreAdminService;

    @InjectMocks
    private KnowledgeIngestService knowledgeIngestService;

    @Captor
    private ArgumentCaptor<List<Document>> documentListCaptor;

    @BeforeEach
    void setUp() {
        // Self-injection does not work via Mockito — set manually to bypass Spring proxy
        ReflectionTestUtils.setField(knowledgeIngestService, "self", knowledgeIngestService);
    }

    // ── ingest(String) overload ───────────────────────────────────────────────

    @Test
    @DisplayName("ingest(String) — defaults source to 'manual_upload' when no metadata provided")
    void ingest_noMetadata_usesDefaultSource() {
        // GIVEN
        when(splitterProperties.getShortTextThreshold()).thenReturn(200);

        // WHEN — single-arg overload, no source provided
        knowledgeIngestService.ingest("Some text content.");

        // THEN — source must default to 'manual_upload'
        verify(vectorStore).add(documentListCaptor.capture());
        assertThat(documentListCaptor.getValue().get(0).getMetadata())
                .containsEntry("source", "manual_upload");
    }

    // ── ingest(String, Map) ───────────────────────────────────────────────────

    @Test
    @DisplayName("ingest — short text is stored as a single chunk without splitting")
    void ingest_shortText_addsSingleDocument() {
        // GIVEN — text shorter than threshold, splitting skipped
        String text = "PGVector uses HNSW indexes for approximate nearest neighbor search.";
        when(splitterProperties.getShortTextThreshold()).thenReturn(200);

        // WHEN
        knowledgeIngestService.ingest(text, Map.of("source", "manual_upload"));

        // THEN
        verify(vectorStore).add(documentListCaptor.capture());
        List<Document> saved = documentListCaptor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getText()).isEqualTo(text);
        assertThat(saved.get(0).getMetadata())
                .containsEntry("source", "manual_upload")
                .containsKey("ingested_at");
    }

    @Test
    @DisplayName("ingest — long text is split into chunks enriched with chunk_index and chunk_total")
    void ingest_longText_producesEnrichedChunks() {
        // GIVEN — text longer than threshold triggers TokenTextSplitter
        String longText = "RAG architecture optimizes LLM responses by providing context. ".repeat(100);
        when(splitterProperties.getShortTextThreshold()).thenReturn(200);
        when(splitterProperties.getChunkSize()).thenReturn(300);
        when(splitterProperties.getEffectiveChunkOverlap()).thenReturn(80);
        when(splitterProperties.getMinChunkSize()).thenReturn(100);
        when(splitterProperties.getMaxChunkSize()).thenReturn(500);

        // WHEN
        knowledgeIngestService.ingest(longText, Map.of("source", "test.pdf"));

        // THEN — all chunks must have chunk metadata regardless of exact count
        verify(vectorStore).add(documentListCaptor.capture());
        List<Document> chunks = documentListCaptor.getValue();
        assertThat(chunks).isNotEmpty();
        chunks.forEach(chunk -> assertThat(chunk.getMetadata())
                .containsKeys("chunk_index", "chunk_total"));
    }

    @Test
    @DisplayName("ingest — blank text performs no actions on vector store or repository")
    void ingest_blankText_performsNoAction() {
        // WHEN
        knowledgeIngestService.ingest("   ", Map.of());

        // THEN — early return before any I/O
        verify(vectorStore, never()).add(anyList());
        verifyNoInteractions(vectorStoreRepository);
    }

    @Test
    @DisplayName("ingest — custom source metadata is preserved in stored chunks")
    void ingest_customSource_usesProvidedSource() {
        // GIVEN
        String customSource = "my-custom-file.pdf";
        when(splitterProperties.getShortTextThreshold()).thenReturn(200);

        // WHEN
        knowledgeIngestService.ingest("Custom source document content.", Map.of("source", customSource));

        // THEN
        verify(vectorStore).add(documentListCaptor.capture());
        assertThat(documentListCaptor.getValue().get(0).getMetadata())
                .containsEntry("source", customSource);
    }

    @Test
    @DisplayName("ingest — wraps VectorStore exception in RuntimeException with descriptive message")
    void ingest_vectorStoreFails_throwsRuntimeException() {
        // GIVEN
        when(splitterProperties.getShortTextThreshold()).thenReturn(200);
        doThrow(new RuntimeException("Connection refused")).when(vectorStore).add(anyList());

        // WHEN & THEN
        assertThatThrownBy(() -> knowledgeIngestService.ingest("Some valid text.", Map.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Vector store ingestion failed");
    }

    // ── deleteDocument() ──────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteDocument — passes ID directly to vector store without UUID parsing")
    void deleteDocument_validUuid_triggersDeletion() {
        // GIVEN
        String validUuid = "f47ac10b-58cc-4372-a567-0e02b2c3d479";

        // WHEN
        knowledgeIngestService.deleteDocument(validUuid);

        // THEN
        verify(vectorStore).delete(List.of(validUuid));
    }

    // ── deleteAllDocuments() ──────────────────────────────────────────────────

    @Test
    @DisplayName("deleteAllDocuments — delegates to VectorStoreAdminService.truncateAll()")
    void deleteAllDocuments_delegatesToAdminService() {
        // WHEN
        knowledgeIngestService.deleteAllDocuments();

        // THEN — destructive operation must go through the admin service, not repo directly
        verify(vectorStoreAdminService).truncateAll();
        verifyNoInteractions(vectorStoreRepository);
    }

    // ── updateDocument() ──────────────────────────────────────────────────────

    @Test
    @DisplayName("updateDocument — deletes existing chunks by source then re-ingests new content")
    void updateDocument_validData_replacesExistingVector() {
        // GIVEN
        String docId = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
        String source = "original-source.txt";
        when(vectorStoreRepository.findSourceById(UUID.fromString(docId)))
                .thenReturn(Optional.of(source));
        when(splitterProperties.getShortTextThreshold()).thenReturn(200);

        // WHEN
        knowledgeIngestService.updateDocument(docId, "Enhanced RAG uses cross-encoders.");

        // THEN — delete via admin service (with guard clause), then ingest new content
        verify(vectorStoreAdminService).deleteBySource(source);
        verify(vectorStore).add(anyList());
    }

    @Test
    @DisplayName("updateDocument — throws 404 ResponseStatusException when document ID not found")
    void updateDocument_notFound_throws404() {
        // GIVEN
        String docId = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
        when(vectorStoreRepository.findSourceById(any(UUID.class))).thenReturn(Optional.empty());

        // WHEN & THEN
        assertThatThrownBy(() -> knowledgeIngestService.updateDocument(docId, "new content"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Document not found");
    }

    // ── getCount() ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getCount — returns value from repository")
    void getCount_returnsValueFromRepo() {
        // GIVEN
        when(vectorStoreRepository.count()).thenReturn(1024L);

        // WHEN & THEN
        assertThat(knowledgeIngestService.getCount()).isEqualTo(1024L);
    }
}