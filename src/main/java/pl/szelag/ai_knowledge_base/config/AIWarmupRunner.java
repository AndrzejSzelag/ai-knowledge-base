package pl.szelag.ai_knowledge_base.config;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import pl.szelag.ai_knowledge_base.service.KnowledgeQueryService;

/**
 * Performs a warm-up on startup to eliminate cold-start latency for the first real user request.
 * Forces JDBC pool initialization and pgvector index loading into RAM.
 * Does NOT invoke the LLM — only the vector store is exercised.
 */
@Component
@Order(2)
public class AIWarmupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AIWarmupRunner.class);

    private final KnowledgeQueryService knowledgeQueryService;

    public AIWarmupRunner(KnowledgeQueryService knowledgeQueryService) {
        this.knowledgeQueryService = knowledgeQueryService;
    }

    /**
     * Runs after the Spring context is fully initialized.
     * Failures are logged as warnings — a warm-up failure must never prevent startup
     * (e.g. when the DB container is still coming up in Docker Compose).
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting AI warm-up (PostgreSQL + pgvector, LLM skipped)...");
        long startNano = System.nanoTime();
        try {
            knowledgeQueryService.warmup();
            log.info("AI warm-up completed in {} ms.",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano));
        } catch (Exception e) {
            log.warn("AI warm-up skipped — first request will experience cold-start latency. Reason: {}",
                    e.getMessage());
        }
    }
}