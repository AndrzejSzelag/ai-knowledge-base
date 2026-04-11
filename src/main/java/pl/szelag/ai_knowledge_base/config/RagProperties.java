package pl.szelag.ai_knowledge_base.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration properties for the RAG (Retrieval-Augmented Generation)
 * pipeline.
 * Maps properties with the 'app.rag' prefix from application configuration
 * files.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    // Retrieval Phase: initial candidate documents retrieved from the vector store.
    private int topK;

    // Retrieval Phase: minimum similarity score (0.0–1.0) for a document to be
    // considered relevant.
    private double similarityThreshold;

    // Prompt Phase: top-ranked documents actually included in the final LLM prompt
    // context.
    private int finalContextDocs;

    // Prompt Phase: max cumulative character count to prevent exceeding LLM token
    // limits.
    private int maxContextChars;

    // Prompt Phase: system instruction template; must contain {context} and
    // {question} placeholders.
    private String systemPromptTemplate;

    // Strategy: whether to include metadata sources (e.g. filenames/URLs) in the
    // response.
    private boolean includeSources = true;

    // Strategy: message returned when no relevant documents are found above the
    // threshold.
    private String fallbackMessage;

    // Performance: timeout for the LLM generation process to avoid hanging
    // requests.
    private Duration timeout;
}