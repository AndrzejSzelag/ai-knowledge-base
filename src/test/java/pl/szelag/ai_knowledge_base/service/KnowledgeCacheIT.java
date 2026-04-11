package pl.szelag.ai_knowledge_base.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import pl.szelag.ai_knowledge_base.repository.VectorStoreRepository;

/**
 * Integration test verifying cache behavior (NOT implementation)
 * in KnowledgeIngestService.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration"
})
@ActiveProfiles("test-local")
class KnowledgeCacheIT {

    @MockitoBean
    private VectorStore vectorStore;

    @Autowired
    private KnowledgeIngestService ingestService;

    @MockitoBean
    private VectorStoreRepository vectorStoreRepository;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetMocks() {
        reset(vectorStoreRepository, vectorStore);
    }

    @Test
    @DisplayName("Should cache getCount() and evict after ingest()")
    void ingest_shouldEvictCountCache_andForceReload() {
        // GIVEN
        when(vectorStoreRepository.count()).thenReturn(10L, 20L);

        // 1. First call → hits DB
        long first = ingestService.getCount();

        // 2. Second call → should use cache (NO DB hit)
        long second = ingestService.getCount();

        assertThat(first).isEqualTo(10L);
        assertThat(second).isEqualTo(10L);

        // DB should be called ONLY ONCE (cache works)
        verify(vectorStoreRepository, times(1)).count();

        // WHEN
        ingestService.ingest("Technical content for testing cache eviction.");

        // THEN
        // After eviction → should hit DB again
        long afterEviction = ingestService.getCount();

        assertThat(afterEviction).isEqualTo(20L);

        // Now DB should be called AGAIN
        verify(vectorStoreRepository, times(2)).count();

        // And ingestion should call vector store
        verify(vectorStore, atLeastOnce()).add(anyList());
    }
}