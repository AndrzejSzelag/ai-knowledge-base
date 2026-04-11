package pl.szelag.ai_knowledge_base.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import pl.szelag.ai_knowledge_base.controller.BaseWebMvcTest;
import pl.szelag.ai_knowledge_base.dto.AskRequest;
import pl.szelag.ai_knowledge_base.dto.IngestRequest;
import pl.szelag.ai_knowledge_base.service.KnowledgeIngestService;
import pl.szelag.ai_knowledge_base.service.KnowledgeQueryService;

/**
 * Integration tests for Spring Security configuration.
 */
@WebMvcTest // all controllers
@Import({ SecurityConfig.class, RestAuthenticationEntryPoint.class })
@TestPropertySource(properties = "app.frontend-url=http://localhost:5173")
class SecurityConfigIT extends BaseWebMvcTest {

    // Required to satisfy Spring context — controllers depend on these services
    @MockitoBean
    KnowledgeIngestService knowledgeIngestService;

    @MockitoBean
    KnowledgeQueryService knowledgeQueryService;

    @Test
    void askAi_publicEndpoint_allowsAnonymousAccess() throws Exception {
        String json = objectMapper.writeValueAsString(new AskRequest("test"));

        mockMvc.perform(post("/api/ai/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void ingestAi_unauthenticated_returns401() throws Exception {
        String json = objectMapper.writeValueAsString(new IngestRequest("test"));

        mockMvc.perform(post("/api/ai/ingest")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ingestAi_authenticatedWithCsrf_allowsAccess() throws Exception {
        String json = objectMapper.writeValueAsString(new IngestRequest("test"));

        mockMvc.perform(post("/api/ai/ingest")
                .with(oauth2Login())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void ingestAi_authenticatedWithoutCsrf_returns403() throws Exception {
        String json = objectMapper.writeValueAsString(new IngestRequest("test"));

        mockMvc.perform(post("/api/ai/ingest")
                .with(oauth2Login())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpoint_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/ai/documents/recent"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authMe_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonApiRequest_unauthenticated_redirects() throws Exception {
        mockMvc.perform(get("/some-page"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void cors_preflightRequest_isAllowed() throws Exception {
        mockMvc.perform(options("/api/ai/ask")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    @Test
    void logout_returns200() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isOk());
    }

    /**
     * Verifies that static resources (like favicon) bypass security filters.
     * Should return 200 OK if the file exists, confirming it's not blocked or
     * redirected.
     */
    @Test
    void staticResource_unauthenticated_ignoresSecurity() throws Exception {
        // WHEN / THEN
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isOk());
    }

}