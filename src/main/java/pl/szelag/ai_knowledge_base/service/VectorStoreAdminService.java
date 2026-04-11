package pl.szelag.ai_knowledge_base.service;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.szelag.ai_knowledge_base.repository.VectorStoreRepository;

/**
 * Encapsulates destructive / administrative operations on the vector store
 * that must not live inside a plain repository interface.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreAdminService {

    private final VectorStoreRepository vectorStoreRepository;

    /**
     * Removes every record from the vector store.
     *
     * <p>
     * Replaces the old {@code TRUNCATE} native query that was incorrectly
     * placed in the repository layer. Uses JPA {@code deleteAll()} so the
     * operation participates in the active transaction and triggers
     * {@code @PreRemove} lifecycle callbacks if needed.</p>
     *
     * <p>
     * <b>Irreversible</b> — call only after explicit user confirmation.</p>
     */
    @Transactional
    public void truncateAll() {
        long count = vectorStoreRepository.count();
        log.warn("Truncating entire vector_store — {} records will be deleted.", count);
        vectorStoreRepository.deleteAll();
        log.info("vector_store truncated successfully.");
    }

    /**
     * Deletes all chunks belonging to the given source.
     *
     * @param source non-null, non-blank source identifier
     * @throws IllegalArgumentException if {@code source} is null or blank
     */
    @Transactional
    public void deleteBySource(@NonNull String source) {
        // Guard clause — prevents accidental bulk deletion caused by a blank
        // source value (SQL NULL != '' semantics would delete nothing, but an
        // empty string would delete every row whose source is '').
        Assert.hasText(source, "source must not be null or blank");

        log.info("Deleting all vector_store chunks for source='{}'", source);
        vectorStoreRepository.deleteBySource(source);
    }
}