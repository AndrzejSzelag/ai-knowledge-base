package pl.szelag.ai_knowledge_base.config;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds PGVector store with JSON data if the store is empty.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final VectorStore vectorStore;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Executes the seed data ingestion on startup.
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {

        // Check if seed file exists in classpath
        Resource resource = resourceLoader.getResource("classpath:test-data.json");
        if (!resource.exists()) {
            log.warn("Seed data file not found: test-data.json");
            return;
        }

        // Avoid duplicates by checking existing record count
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Integer.class);
        if (count != null && count > 0) {
            log.info("Vector store already contains {} documents. Skipping initialization.", count);
            return;
        }

        log.info("Loading seed data for AI Knowledge Base...");

        // Use try-with-resources for automatic stream closing
        try (var inputStream = resource.getInputStream()) {

            // Parse JSON file into a raw data list
            List<Map<String, Object>> rawData = objectMapper.readValue(
                    inputStream,
                    new TypeReference<>() {
                    });

            // Convert raw maps to AI Document objects
            List<Document> documents = rawData.stream()
                    .map(item -> {
                        String content = (String) item.get("content");

                        // Convert raw metadata map to a typed Map<String, Object> via ObjectMapper
                        Map<String, Object> metadata = objectMapper.convertValue(
                                item.get("metadata"),
                                new TypeReference<Map<String, Object>>() {
                                });

                        return new Document(content, metadata);
                    })
                    .toList();

            // Generate embeddings and save to PGVector
            vectorStore.add(documents);
            
            log.info("Successfully ingested {} test definitions into PGVector", documents.size());
        } catch (Exception e) {
            log.error("Failed to load seed data: {}", e.getMessage());
        }
    }
}