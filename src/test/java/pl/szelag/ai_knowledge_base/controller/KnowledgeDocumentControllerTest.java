package pl.szelag.ai_knowledge_base.controller;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import pl.szelag.ai_knowledge_base.config.RestAuthenticationEntryPoint;
import pl.szelag.ai_knowledge_base.config.SecurityConfig;
import pl.szelag.ai_knowledge_base.dto.DocumentDto;
import pl.szelag.ai_knowledge_base.service.KnowledgeIngestService;

@WebMvcTest(KnowledgeDocumentController.class)
@Import({ SecurityConfig.class, RestAuthenticationEntryPoint.class })
@TestPropertySource(properties = "app.frontend-url=http://localhost:3000")
class KnowledgeDocumentControllerTest extends BaseWebMvcTest {

    @MockitoBean
    private KnowledgeIngestService knowledgeIngestService;

    // ── GET /api/ai/documents ─────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /documents — authenticated user receives paginated document list")
    void getDocuments_authenticated_returns200() throws Exception {
        // GIVEN
        DocumentDto dto = new DocumentDto("id-1", "Some content", Map.of("source", "test.pdf"), "0.99");
        Page<DocumentDto> page = new PageImpl<>(List.of(dto));
        when(knowledgeIngestService.getDocuments(any(Pageable.class))).thenReturn(page);

        // WHEN & THEN
        mockMvc.perform(get("/api/ai/documents")
                .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].documentId").value("id-1"))
                .andExpect(jsonPath("$.content[0].content").value("Some content"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /documents — returns empty page when no documents exist")
    void getDocuments_emptyStore_returnsEmptyPage() throws Exception {
        // GIVEN
        when(knowledgeIngestService.getDocuments(any(Pageable.class)))
                .thenReturn(Page.empty());

        // WHEN & THEN
        mockMvc.perform(get("/api/ai/documents")
                .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("GET /documents — unauthenticated returns 401")
    void getDocuments_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/ai/documents"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/ai/documents/count ───────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /documents/count — authenticated user receives total count")
    void getCount_authenticated_returns200() throws Exception {
        // GIVEN
        when(knowledgeIngestService.getCount()).thenReturn(42L);

        // WHEN & THEN
        mockMvc.perform(get("/api/ai/documents/count")
                .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(42));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /documents/count — returns 0 when store is empty")
    void getCount_emptyStore_returnsZero() throws Exception {
        // GIVEN
        when(knowledgeIngestService.getCount()).thenReturn(0L);

        // WHEN & THEN
        mockMvc.perform(get("/api/ai/documents/count")
                .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(0));
    }

    @Test
    @DisplayName("GET /documents/count — unauthenticated returns 401")
    void getCount_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/ai/documents/count"))
                .andExpect(status().isUnauthorized());
    }
}