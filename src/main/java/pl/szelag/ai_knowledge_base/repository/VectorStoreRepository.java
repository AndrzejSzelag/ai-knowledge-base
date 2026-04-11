package pl.szelag.ai_knowledge_base.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pl.szelag.ai_knowledge_base.entity.VectorStoreEntity;

/**
 * Accesses the "vector_store" table via native SQL (pgvector unsupported by
 * Hibernate).
 *
 * <p>
 * Required indexes for acceptable performance
 * </p>
 * :
 * 
 * <pre>
 *   CREATE INDEX idx_vector_store_ingested_at
 *       ON vector_store (((metadata->>'ingested_at')::bigint) DESC NULLS LAST);
 *
 *   CREATE INDEX idx_vector_store_source
 *       ON vector_store ((metadata->>'source'));
 * </pre>
 */
@Repository
public interface VectorStoreRepository extends JpaRepository<VectorStoreEntity, UUID> {

       /**
        * Returns metadata.source value for given document id.
        *
        * <p>
        * Uses {@code CAST} to avoid ClassCastException when Spring Data maps
        * a raw pgvector/JSONB scalar through a native query.
        * </p>
        */
       @Query(value = "SELECT CAST(metadata->>'source' AS TEXT) FROM vector_store WHERE id = :id", nativeQuery = true)
       Optional<String> findSourceById(@Param("id") UUID id);

       /**
        * Returns paginated document projection ordered by ingestion timestamp.
        * Includes source extracted from JSONB metadata column.
        *
        * <p>
        * <b>Requires</b> the expression index on
        * {@code (metadata->>'ingested_at')::bigint}
        * — see class-level Javadoc. Without it, this query performs a full table scan
        * for every page request.
        * </p>
        */
       @Query(value = """
                     SELECT id, content, metadata->>'source' AS source
                     FROM vector_store
                     ORDER BY (metadata->>'ingested_at')::bigint DESC NULLS LAST
                     OFFSET :#{#pageable.offset}
                     LIMIT :#{#pageable.pageSize}
                     """, countQuery = "SELECT count(*) FROM vector_store", nativeQuery = true)
       Page<DocumentProjection> findAllSimplified(Pageable pageable);

       /**
        * Deletes all document chunks for given metadata.source.
        *
        * @param source non-null, non-blank source identifier; validated before
        *               the query is issued to prevent accidental bulk deletion.
        * @throws IllegalArgumentException if {@code source} is null or blank
        */
       @Modifying
       @Transactional
       @Query(value = "DELETE FROM vector_store WHERE metadata->>'source' = :source", nativeQuery = true)
       void deleteBySource(@NonNull @Param("source") String source);

       // -------------------------------------------------------------------------
       // NOTE: TRUNCATE has been intentionally removed from this repository.
       // Full-table wipes are a destructive DDL operation that belongs in a
       // dedicated service method, not in a data-access interface.
       // Use VectorStoreAdminService.truncateAll() instead, which wraps deleteAll()
       // (inherited from JpaRepository) inside an explicit transaction with
       // proper logging and authorisation checks.
       // -------------------------------------------------------------------------
}