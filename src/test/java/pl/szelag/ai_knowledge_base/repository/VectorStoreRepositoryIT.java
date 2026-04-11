package pl.szelag.ai_knowledge_base.repository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import pl.szelag.ai_knowledge_base.service.VectorStoreAdminService;

@SpringBootTest
@ActiveProfiles("test-local")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VectorStoreRepositoryIT {

    @Autowired
    private VectorStoreRepository vectorStoreRepository;

    @Autowired
    private VectorStoreAdminService vectorStoreAdminService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // These mocks prevent Spring AI from trying to connect to real AI models during context startup
    @MockitoBean
    private EmbeddingModel embeddingModel;

    @MockitoBean
    private ChatModel chatModel;

    @BeforeEach
    void clearData() {
        jdbcTemplate.execute("TRUNCATE TABLE vector_store CASCADE");
    }

    @Test
    @DisplayName("Should return paginated data with total element count")
    void findAllSimplified_returnsFullPageDataWithCount() {
        // GIVEN
        insertDocument(UUID.randomUUID(), "First", 1000L);
        insertDocument(UUID.randomUUID(), "Second", 2000L);
        insertDocument(UUID.randomUUID(), "Third", 3000L);

        // WHEN
        Page<DocumentProjection> page = vectorStoreRepository.findAllSimplified(PageRequest.of(0, 2));

        // THEN
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).getContent()).isEqualTo("Third");
    }

    @Test
    @DisplayName("Should wipe all entries from the vector store via VectorStoreAdminService")
    void truncateAll_removesAllData() {
        // GIVEN
        insertDocument(UUID.randomUUID(), "Data to be nuked", 1000L);
        assertThat(vectorStoreRepository.count()).isPositive();

        // WHEN
        vectorStoreAdminService.truncateAll();

        // THEN
        assertThat(vectorStoreRepository.count()).isZero();
    }

    @Test
    @DisplayName("Should delete only documents matching the specific source in metadata")
    void deleteBySource_removesOnlyTargetedDocuments() {
        // GIVEN
        insertDocumentWithSource(UUID.randomUUID(), "Doc from source A", "A", 1000L);
        insertDocumentWithSource(UUID.randomUUID(), "Doc from source B", "B", 2000L);

        // WHEN
        vectorStoreAdminService.deleteBySource("A");

        // THEN
        assertThat(vectorStoreRepository.count()).isEqualTo(1L);
        Page<DocumentProjection> remaining = vectorStoreRepository.findAllSimplified(PageRequest.of(0, 10));
        assertThat(remaining.getContent().get(0).getContent()).isEqualTo("Doc from source B");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when source is blank")
    void deleteBySource_throwsOnBlankSource() {
        // WHEN / THEN
        assertThatThrownBy(() -> vectorStoreAdminService.deleteBySource(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source must not be null or blank");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when source is null")
    void deleteBySource_throwsOnNullSource() {
        // WHEN / THEN
        assertThatThrownBy(() -> vectorStoreAdminService.deleteBySource(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    private void insertDocument(UUID id, String content, long ingestedAt) {
        insertDocumentWithSource(id, content, "manual", ingestedAt);
    }

    private void insertDocumentWithSource(UUID id, String content, String source, long ingestedAt) {
        String metadataJson = String.format("{\"source\": \"%s\", \"ingested_at\": %d}", source, ingestedAt);
        jdbcTemplate.update(
                "INSERT INTO vector_store (id, content, metadata, embedding) VALUES (?, ?, ?::jsonb, NULL)",
                id, content, metadataJson);
    }
}