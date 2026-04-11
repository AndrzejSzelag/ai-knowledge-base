package pl.szelag.ai_knowledge_base.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only JPA mapping for the Spring AI managed {@code vector_store} table.
 * All writes are delegated to Spring AI's {@code VectorStore} abstraction.
 */
@Entity
@Table(name = "vector_store")
@Immutable
@NoArgsConstructor
@Getter
public class VectorStoreEntity {

    @Id
    private UUID id;

    @Column(columnDefinition = "TEXT")
    private String content;

    /** Arbitrary metadata (source, ingested_at, etc.) stored as JSONB. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    /** 1024-dim pgvector embedding, serialized via {@link VectorConverter}. */
    @Convert(converter = VectorConverter.class)
    @Column(name = "embedding", columnDefinition = "vector(1024)")
    private float[] embedding;

    /**
     * Returns cosine similarity between this embedding and {@code other}.
     * Range: [-1.0, 1.0]. Returns 0.0 if either vector is null or zero-magnitude.
     * <p>
     * Provided for in-memory testing and diagnostics — production similarity
     * search is handled by pgvector on the database side.
     */
    public double cosineSimilarity(float[] other) {
        if (embedding == null || other == null || embedding.length != other.length)
            return 0.0;

        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < embedding.length; i++) {
            dot += (double) embedding[i] * other[i];
            normA += (double) embedding[i] * embedding[i];
            normB += (double) other[i] * other[i];
        }
        if (normA == 0.0 || normB == 0.0)
            return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}