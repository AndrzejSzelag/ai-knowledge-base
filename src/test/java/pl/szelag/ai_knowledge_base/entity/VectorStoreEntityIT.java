package pl.szelag.ai_knowledge_base.entity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import jakarta.transaction.Transactional;

/**
 * Integration tests for VectorStoreEntity mapping with PostgreSQL.
 * Verifies JSONB conversion, pgvector column via VectorConverter, and
 * immutability.
 */
@DataJpaTest
@ActiveProfiles("test-local")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VectorStoreEntityIT {

        @Autowired
        private TestEntityManager entityManager;

        // -------------------------------------------------------------------------
        // Helpers
        // -------------------------------------------------------------------------

        /**
         * Converts float[] to the "[f1,f2,...]" string format accepted by pgvector.
         * Mirrors VectorConverter.convertToDatabaseColumn so we can pass it in native
         * SQL.
         */
        private String toVectorLiteral(float[] values) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < values.length; i++) {
                        if (i > 0)
                                sb.append(",");
                        sb.append(values[i]);
                }
                return sb.append("]").toString();
        }

        private void insertRow(UUID id, String content, String metadataJson, float[] embedding) {
                entityManager.getEntityManager().createNativeQuery(
                                "INSERT INTO vector_store (id, content, metadata, embedding) " +
                                                "VALUES (:id, :content, CAST(:metadata AS jsonb), CAST(:embedding AS vector(1024)))")
                                .setParameter("id", id)
                                .setParameter("content", content)
                                .setParameter("metadata", metadataJson)
                                .setParameter("embedding", toVectorLiteral(embedding))
                                .executeUpdate();
        }

        // -------------------------------------------------------------------------
        // Mapping tests
        // -------------------------------------------------------------------------

        @Test
        @Transactional
        void shouldMapJsonbMetadataAndEmbeddingCorrectly() {
                UUID id = UUID.randomUUID();
                float[] embedding = new float[1024];

                insertRow(id, "Test document content",
                                "{\"source\":\"manual\",\"ingested_at\":1710260000}", embedding);
                entityManager.clear();

                VectorStoreEntity entity = entityManager.find(VectorStoreEntity.class, id);

                assertThat(entity).isNotNull();
                assertThat(entity.getContent()).isEqualTo("Test document content");

                Map<String, Object> metadata = entity.getMetadata();
                assertThat(metadata).isNotNull()
                                .containsEntry("source", "manual");
                assertThat(metadata.get("ingested_at")).isNotNull();

                assertThat(entity.getEmbedding()).isNotNull()
                                .hasSize(1024)
                                .containsOnly(0.0f);
        }

        @Test
        @Transactional
        void shouldAllowMultipleEntitiesAndDistinctIds() {
                UUID id1 = UUID.randomUUID();
                UUID id2 = UUID.randomUUID();

                insertRow(id1, "Doc 1", "{\"source\":\"auto\"}", new float[1024]);
                insertRow(id2, "Doc 2", "{\"source\":\"manual\"}", new float[1024]);
                entityManager.clear();

                VectorStoreEntity e1 = entityManager.find(VectorStoreEntity.class, id1);
                VectorStoreEntity e2 = entityManager.find(VectorStoreEntity.class, id2);

                assertThat(e1).isNotNull();
                assertThat(e2).isNotNull();
                assertThat(e1.getId()).isNotEqualTo(e2.getId());
                assertThat(e1.getContent()).isEqualTo("Doc 1");
                assertThat(e2.getContent()).isEqualTo("Doc 2");
        }

        @Test
        @Transactional
        void shouldNotAllowUpdatesToImmutableEntity() {
                UUID id = UUID.randomUUID();
                insertRow(id, "Immutable doc", "{\"source\":\"test\"}", new float[1024]);
                entityManager.clear();

                VectorStoreEntity entity = entityManager.find(VectorStoreEntity.class, id);

                // @Immutable means Hibernate silently ignores dirty checks — no UPDATE is
                // issued
                // Verify by checking there are no setters on the entity (compile-time
                // guarantee)
                assertThat(entity.getClass().getMethods())
                                .extracting(java.lang.reflect.Method::getName)
                                .filteredOn(name -> name.startsWith("set"))
                                .isEmpty();
        }

        // -------------------------------------------------------------------------
        // cosineSimilarity tests (unit-style, no DB round-trip needed)
        // -------------------------------------------------------------------------

        @Test
        @Transactional
        void cosineSimilarity_shouldReturnOneForIdenticalNonZeroVectors() {
                UUID id = UUID.randomUUID();
                float[] embedding = new float[1024];
                embedding[0] = 1.0f;
                embedding[1] = 0.5f;

                insertRow(id, "Similarity doc", "{\"source\":\"test\"}", embedding);
                entityManager.clear();

                VectorStoreEntity entity = entityManager.find(VectorStoreEntity.class, id);

                double similarity = entity.cosineSimilarity(entity.getEmbedding());
                assertThat(similarity).isCloseTo(1.0, within(1e-6));
        }

        @Test
        @Transactional
        void cosineSimilarity_shouldReturnZeroForZeroVector() {
                UUID id = UUID.randomUUID();
                float[] embedding = new float[1024]; // all zeros

                insertRow(id, "Zero vector doc", "{\"source\":\"test\"}", embedding);
                entityManager.clear();

                VectorStoreEntity entity = entityManager.find(VectorStoreEntity.class, id);

                assertThat(entity.cosineSimilarity(new float[1024])).isEqualTo(0.0);
        }

        @Test
        @Transactional
        void cosineSimilarity_shouldReturnZeroForNullInput() {
                UUID id = UUID.randomUUID();
                insertRow(id, "Null input doc", "{\"source\":\"test\"}", new float[1024]);
                entityManager.clear();

                VectorStoreEntity entity = entityManager.find(VectorStoreEntity.class, id);

                assertThat(entity.cosineSimilarity(null)).isEqualTo(0.0);
        }

        @Test
        @Transactional
        void cosineSimilarity_shouldReturnZeroForMismatchedLength() {
                UUID id = UUID.randomUUID();
                insertRow(id, "Mismatch doc", "{\"source\":\"test\"}", new float[1024]);
                entityManager.clear();

                VectorStoreEntity entity = entityManager.find(VectorStoreEntity.class, id);

                assertThat(entity.cosineSimilarity(new float[512])).isEqualTo(0.0);
        }
}