package pl.szelag.ai_knowledge_base.controller;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Base support class for WebMvc tests.
 * Subclasses must declare @WebMvcTest themselves.
 */
public abstract class BaseWebMvcTest {

        @Autowired
        protected MockMvc mockMvc;

        @Autowired
        protected ObjectMapper objectMapper;

        /**
         * Mocks for Spring AI components to prevent unintended bean instantiation
         * and external dependencies during WebMvc tests.
         *
         * Although @WebMvcTest loads a limited application context, certain
         * configurations (e.g., imported security config or indirect dependencies) may
         * trigger creation of beans related to VectorStore or EmbeddingModel.
         *
         * Providing mocks ensures that no database or infrastructure beans (e.g.
         * DataSource) are required, keeping the test context lightweight and isolated.
         */
        @MockitoBean
        protected VectorStore vectorStore;

        @MockitoBean
        protected EmbeddingModel embeddingModel;
}