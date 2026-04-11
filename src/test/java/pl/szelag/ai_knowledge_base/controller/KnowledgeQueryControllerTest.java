package pl.szelag.ai_knowledge_base.controller;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import pl.szelag.ai_knowledge_base.config.RestAuthenticationEntryPoint;
import pl.szelag.ai_knowledge_base.config.SecurityConfig;
import pl.szelag.ai_knowledge_base.dto.AskRequest;
import pl.szelag.ai_knowledge_base.dto.AskResponse;
import pl.szelag.ai_knowledge_base.dto.SearchResult;
import pl.szelag.ai_knowledge_base.service.KnowledgeIngestService;
import pl.szelag.ai_knowledge_base.service.KnowledgeQueryService;
import reactor.core.publisher.Flux;

@WebMvcTest(KnowledgeQueryController.class)
@Import({ SecurityConfig.class, RestAuthenticationEntryPoint.class })
@TestPropertySource(properties = "app.frontend-url=http://localhost:3000")
class KnowledgeQueryControllerTest extends BaseWebMvcTest {

        @MockitoBean
        private KnowledgeQueryService knowledgeQueryService;

        @MockitoBean
        private KnowledgeIngestService knowledgeIngestService;

        // ── /api/ai/ask (synchronous) ─────────────────────────────────────────────

        @Test
        @DisplayName("POST /ask — valid question returns 200 with answer")
        void ask_validQuestion_returns200() throws Exception {
                // GIVEN — service returns a pre-built answer
                AskResponse fakeResponse = new AskResponse("SQL answer", List.of());
                when(knowledgeQueryService.ask(anyString())).thenReturn(fakeResponse);

                // WHEN & THEN
                mockMvc.perform(post("/api/ai/ask")
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new AskRequest("What is SQL?"))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.answer").value("SQL answer"));
        }

        @Test
        @DisplayName("POST /ask — blank question returns 400")
        void ask_blankQuestion_returns400() throws Exception {
                // GIVEN — empty question should fail @Valid validation
                mockMvc.perform(post("/api/ai/ask")
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new AskRequest(""))))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /ask — service throws RuntimeException returns 500")
        void ask_serviceThrowsException_returns500() throws Exception {
                // GIVEN — simulates an unexpected infrastructure failure
                when(knowledgeQueryService.ask(anyString()))
                                .thenThrow(new RuntimeException("Service Error"));

                // WHEN & THEN — GlobalExceptionHandler should translate to 500
                mockMvc.perform(post("/api/ai/ask")
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new AskRequest("test"))))
                                .andExpect(status().isInternalServerError())
                                .andExpect(jsonPath("$.detail").value("Service unavailable. Please try again."));
        }

        // ── /api/ai/ask-streaming (SSE) ───────────────────────────────────────────

        @Test
        @DisplayName("POST /ask-streaming — valid question streams SSE tokens")
        void askStreaming_validQuestion_returnsSse() throws Exception {
                // GIVEN — context is found, LLM streams two tokens
                when(knowledgeQueryService.performSearch(anyString()))
                                .thenReturn(new SearchResult("some retrieved context", List.of()));
                when(knowledgeQueryService.streamAnswer(anyString(), anyString(), anyLong()))
                                .thenReturn(Flux.just("Hello", " World"));

                // WHEN — start async SSE request
                MvcResult mvcResult = mockMvc.perform(post("/api/ai/ask-streaming")
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new AskRequest("test"))))
                                .andExpect(status().isOk())
                                .andReturn();

                // THEN — dispatch async and verify response body contains streamed tokens
                mockMvc.perform(asyncDispatch(mvcResult))
                                .andExpect(status().isOk())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                                                .contains("Hello")
                                                .contains("World"));
        }

        @Test
        @DisplayName("POST /ask-streaming — no context found returns info message")
        void askStreaming_noContext_returnsInfoMessage() throws Exception {
                // GIVEN — similarity search returned nothing (null context)
                when(knowledgeQueryService.performSearch(anyString()))
                                .thenReturn(new SearchResult(null, List.of()));

                // WHEN & THEN — controller emits a single noContextEvent without calling LLM
                MvcResult mvcResult = mockMvc.perform(post("/api/ai/ask-streaming")
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new AskRequest("test"))))
                                .andExpect(status().isOk())
                                .andReturn();

                mockMvc.perform(asyncDispatch(mvcResult))
                                .andExpect(status().isOk())
                                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                                                .contains("I'm sorry"));
        }

        @Test
        @DisplayName("POST /ask-streaming — LLM stream error emits error SSE event")
        void askStreaming_llmThrowsError_returnsErrorEvent() throws Exception {
                when(knowledgeQueryService.performSearch(anyString()))
                                .thenReturn(new SearchResult("some context", List.of()));
                when(knowledgeQueryService.streamAnswer(anyString(), anyString(), anyLong()))
                                .thenReturn(Flux.error(new RuntimeException("LLM failure")));

                MvcResult mvcResult = mockMvc.perform(post("/api/ai/ask-streaming")
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new AskRequest("test"))))
                                .andExpect(status().isOk())
                                .andReturn();

                mvcResult.getAsyncResult(5000); // czekaj na zakończenie async

                mockMvc.perform(asyncDispatch(mvcResult))
                                .andExpect(status().isOk())
                                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                                                .contains("Error during AI generation."));
        }
}