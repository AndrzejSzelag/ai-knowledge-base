package pl.szelag.ai_knowledge_base.dto;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

/**
 * Unit tests for the SearchResult record.
 */
@JsonTest
class SearchResultTest {

    @Autowired
    private JacksonTester<SearchResult> json;

    @Test
    void shouldCorrectlyStoreData() {
        // GIVEN
        String context = "Retrieved context for user@example.com";
        DocumentDto doc = new DocumentDto(
                "id-1",
                "Retrieved text content",
                Map.of("source", "vector-db"), "1.0");
        List<DocumentDto> docs = List.of(doc);

        // WHEN
        SearchResult result = new SearchResult(context, docs);

        // THEN
        assertThat(result.context()).isEqualTo(context); // Zmieniono z prompt()
        assertThat(result.relatedDocs()).hasSize(1);
        assertThat(result.relatedDocs().get(0)).isEqualTo(doc);
    }

    @Test
    void shouldSerializeSearchResultToJson() throws Exception {
        // GIVEN
        SearchResult result = new SearchResult(
                "System context for example.com",
                List.of(new DocumentDto("uuid-1", "Context", Map.of(), "1.0")));

        // WHEN
        var jsonResult = json.write(result);

        // THEN
        assertThat(jsonResult).hasJsonPathStringValue("$.context"); // Zmieniono z prompt
        assertThat(jsonResult).extractingJsonPathStringValue("$.context")
                .isEqualTo("System context for example.com");

        assertThat(jsonResult).hasJsonPathArrayValue("$.relatedDocs");
        assertThat(jsonResult).extractingJsonPathStringValue("$.relatedDocs[0].documentId")
                .isEqualTo("uuid-1");
    }

    @Test
    void shouldHandleEmptyDocumentList() {
        // GIVEN
        String context = "No context found.";

        // WHEN
        SearchResult result = new SearchResult(context, List.of());

        // THEN
        assertThat(result.context()).isEqualTo(context);
        assertThat(result.relatedDocs()).isEmpty();
    }
}