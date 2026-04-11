package pl.szelag.ai_knowledge_base.controller;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import pl.szelag.ai_knowledge_base.config.RestAuthenticationEntryPoint;
import pl.szelag.ai_knowledge_base.config.SecurityConfig;
import pl.szelag.ai_knowledge_base.dto.IngestRequest;
import pl.szelag.ai_knowledge_base.service.KnowledgeIngestService;

@WebMvcTest(KnowledgeIngestController.class)
@Import({ SecurityConfig.class, RestAuthenticationEntryPoint.class })
@TestPropertySource(properties = "app.frontend-url=http://localhost:3000")
class KnowledgeIngestControllerTest extends BaseWebMvcTest {

    @MockitoBean
    private KnowledgeIngestService knowledgeIngestService;

    private OAuth2User authenticatedUser;

    @BeforeEach
    void setUp() {
        authenticatedUser = new DefaultOAuth2User(
                Collections.emptyList(),
                Map.of("sub", "user-123"),
                "sub");
    }

    @Test
    @DisplayName("Should return 201 Created when ingestion request is valid")
    void ingest_validRequest_returns201() throws Exception {
        // GIVEN
        String content = "SQL is a query language";
        String json = objectMapper.writeValueAsString(new IngestRequest(content));

        // WHEN
        mockMvc.perform(post("/api/ai/ingest")
                .with(oauth2Login().oauth2User(authenticatedUser))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                // THEN
                .andExpect(status().isCreated());

        verify(knowledgeIngestService).ingest(content);
    }

    @Test
    @DisplayName("Should return 200 OK when update request is valid")
    void update_validRequest_returns200() throws Exception {
        // GIVEN
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        String newContent = "Updated content";
        String json = objectMapper.writeValueAsString(new IngestRequest(newContent));

        // WHEN
        mockMvc.perform(put("/api/ai/ingest/{documentId}", uuid)
                .with(oauth2Login().oauth2User(authenticatedUser))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                // THEN
                .andExpect(status().isOk());

        verify(knowledgeIngestService).updateDocument(uuid, newContent);
    }

    @Test
    @DisplayName("Should return 204 No Content when deletion is successful")
    void delete_validUuid_returns204() throws Exception {
        // GIVEN
        String uuid = "550e8400-e29b-41d4-a716-446655440000";

        // WHEN
        mockMvc.perform(delete("/api/ai/ingest/{documentId}", uuid)
                .with(oauth2Login().oauth2User(authenticatedUser))
                .with(csrf()))
                // THEN
                .andExpect(status().isNoContent());

        verify(knowledgeIngestService).deleteDocument(uuid);
    }

    @Test
    @DisplayName("Should return 400 Bad Request when ingestion text is blank")
    void ingest_blankText_returns400() throws Exception {
        // GIVEN
        String json = objectMapper.writeValueAsString(new IngestRequest(""));

        // WHEN
        mockMvc.perform(post("/api/ai/ingest")
                .with(oauth2Login().oauth2User(authenticatedUser))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                // THEN
                .andExpect(status().isBadRequest());

        verifyNoInteractions(knowledgeIngestService);
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when user is not logged in")
    void ingest_unauthenticated_returns401() throws Exception {
        // GIVEN
        String json = objectMapper.writeValueAsString(new IngestRequest("Test text"));

        // WHEN & THEN — no oauth2Login(), so Spring Security rejects the request
        mockMvc.perform(post("/api/ai/ingest")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isUnauthorized());
    }
}