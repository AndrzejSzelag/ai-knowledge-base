package pl.szelag.ai_knowledge_base.dto;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

/**
 * Consolidated suite for JSON serialization and deserialization tests across
 * all DTO records.
 * 
 * <p>
 * This class acts as the "Master Registry" for API contracts, ensuring that
 * the communication between the Java backend and the frontend remains
 * consistent.
 * 
 * It verifies field naming conventions, nested structures, and data type
 * integrity.
 * </p>
 */
@JsonTest
class DtoSerializationTest {

    @Autowired
    private JacksonTester<AskResponse> askResponseJson;

    @Autowired
    private JacksonTester<IngestRequest> ingestRequestJson;

    @Autowired
    private JacksonTester<DocumentDto> documentDtoJson;

    @Autowired
    private JacksonTester<UserProfileResponse> userProfileJson;

    @Test
    void shouldSerializeAskResponseWithNestedDocuments() throws Exception {
        // GIVEN: A response containing an AI answer and source document fragments
        DocumentDto doc = new DocumentDto("doc-001", "Context for user@example.com", Map.of("source", "manual"), "0.99");
        AskResponse response = new AskResponse("AI generated answer", List.of(doc));

        // WHEN: Serializing the object to JSON
        var result = askResponseJson.write(response);

        // THEN: Verify the JSON paths and values
        assertThat(result).hasJsonPathStringValue("$.answer", "AI generated answer");
        assertThat(result).extractingJsonPathStringValue("$.relatedDocs[0].documentId").isEqualTo("doc-001");
        assertThat(result).extractingJsonPathStringValue("$.relatedDocs[0].metadata.source").isEqualTo("manual");
    }

    @Test
    void shouldDeserializeIngestRequestCorrectly() throws Exception {
        // GIVEN: A raw JSON payload representing new knowledge to be ingested
        String jsonContent = "{\"text\": \"Knowledge to be ingested into example.com system\"}";

        // WHEN: Parsing the JSON into a record
        IngestRequest result = ingestRequestJson.parseObject(jsonContent);

        // THEN: Ensure the field is correctly mapped
        assertThat(result.text()).isEqualTo("Knowledge to be ingested into example.com system");
    }

    @Test
    void shouldHandleDiverseMetadataTypesInDocumentDto() throws Exception {
        // GIVEN: Metadata containing various data types (Double, Integer, Boolean)
        Map<String, Object> metadata = Map.of(
                "relevance", 0.95,
                "pageNumber", 10,
                "isTrusted", true);
        DocumentDto dto = new DocumentDto("id-1", "content", metadata, "0.99");

        // WHEN: Writing to JSON
        var result = documentDtoJson.write(dto);

        // THEN: Verify that data types are preserved during serialization
        assertThat(result).extractingJsonPathNumberValue("$.metadata.relevance").isEqualTo(0.95);
        assertThat(result).extractingJsonPathNumberValue("$.metadata.pageNumber").isEqualTo(10);
        assertThat(result).extractingJsonPathBooleanValue("$.metadata.isTrusted").isTrue();
    }

    @Test
    void shouldSerializeEmptyRelatedDocsAsEmptyArray() throws Exception {
        // GIVEN: A response where no related documents were found (fallback scenario)
        AskResponse response = AskResponse.fallback("No data found for example.com");

        // WHEN: Serializing to JSON
        var result = askResponseJson.write(response);

        // THEN: Ensure the collection is an empty array, not null
        assertThat(result).hasJsonPathArrayValue("$.relatedDocs");
        assertThat(result).extractingJsonPathArrayValue("$.relatedDocs").isEmpty();
    }

    @Test
    void shouldSerializeUserProfileCorrectly() throws Exception {
        // GIVEN: A complete user profile structure
        UserProfileResponse profile = new UserProfileResponse(
                "Andrzej Szelag", "andrzej@example.com", "photo.jpg", "Andrzej", "Szelag");

        // WHEN: Serializing to JSON
        var result = userProfileJson.write(profile);

        // THEN: Verify core profile fields
        assertThat(result).hasJsonPathStringValue("$.name", "Andrzej Szelag");
        assertThat(result).hasJsonPathStringValue("$.email", "andrzej@example.com");
    }

    @Test
    void shouldDeserializeUserProfileCorrectly() throws Exception {
        // GIVEN: A JSON representation of a user profile
        String json = "{\"name\":\"Andrzej\",\"email\":\"andrzej@example.com\"}";

        // WHEN: Parsing the JSON string
        UserProfileResponse result = userProfileJson.parseObject(json);

        // THEN: Verify mapped values
        assertThat(result.name()).isEqualTo("Andrzej");
        assertThat(result.email()).isEqualTo("andrzej@example.com");
    }
}