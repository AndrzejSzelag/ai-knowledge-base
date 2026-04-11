package pl.szelag.ai_knowledge_base.config;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import pl.szelag.ai_knowledge_base.service.VectorStoreAdminService;

@SpringBootTest
@ActiveProfiles("test-local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DataInitializerIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private VectorStoreAdminService vectorStoreAdminService;

    @Autowired
    private DataInitializer dataInitializer;

    @Test
    @Order(1)
    @DisplayName("Should verify that seed data is correctly ingested on startup")
    void shouldVerifyDataIngestionOnStartup() throws Exception {
        vectorStoreAdminService.truncateAll();
        dataInitializer.run(null);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store", Integer.class);

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("What is RAG?")
                        .topK(1)
                        .similarityThreshold(0.1)
                        .build());

        assertThat(count).isNotNull().isGreaterThan(0);
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getMetadata()).containsKey("source");
    }

    @Test
    @Order(2)
    @DisplayName("Should filter search results by subcategory metadata")
    void shouldFilterResultsByCategory() {
        var filterExpression = new FilterExpressionBuilder()
                .eq("subcategory", "Vector Databases")
                .build();

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("database")
                        .topK(5)
                        .similarityThreshold(0.01)
                        .filterExpression(filterExpression)
                        .build());

        assertThat(results)
                .as("Should find at least one document in 'Vector Databases' subcategory")
                .isNotEmpty();
        results.forEach(doc -> assertThat(doc.getMetadata().get("subcategory"))
                .as("Every result must belong to 'Vector Databases' subcategory")
                .isEqualTo("Vector Databases"));
    }

    @Test
    @Order(3)
    @DisplayName("Should isolate results by subcategory")
    void shouldNotReturnResultsFromOtherCategories() {
        var filterExpression = new FilterExpressionBuilder()
                .eq("subcategory", "Vector Databases")
                .build();

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("Spring AI framework")
                        .topK(10)
                        .filterExpression(filterExpression)
                        .build());

        assertThat(results.stream()
                .anyMatch(doc -> "Frameworks".equals(doc.getMetadata().get("subcategory"))))
                .as("Should not find documents from 'Frameworks' subcategory")
                .isFalse();
    }
}
