package pl.szelag.ai_knowledge_base.config;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * Integration tests for RagProperties configuration. Uses a lightweight context
 * to verify property mapping without starting database or full application
 * services.
 */
@Slf4j
@SpringJUnitConfig(initializers = ConfigDataApplicationContextInitializer.class)
@EnableConfigurationProperties(RagProperties.class)
@ActiveProfiles("test-local")
class RagConfigurationTest {

    @Autowired
    private RagProperties ragProperties;

    @Test
    @DisplayName("Should load all RAG properties from application-test-local.yml")
    void shouldLoadRagPropertiesFromTestProfile() {
        // GIVEN
        // Properties are loaded from application-test-local.yml via ActiveProfiles

        // WHEN + THEN
        // Core retrieval parameters
        assertThat(ragProperties.getTopK())
                .as("TopK should match value in application-test-local.yml")
                .isEqualTo(3);

        assertThat(ragProperties.getSimilarityThreshold()).isGreaterThan(0.0);
        assertThat(ragProperties.getFinalContextDocs()).isEqualTo(3);
        assertThat(ragProperties.getMaxContextChars()).isEqualTo(3000);

        // Prompt & strategy parameters
        assertThat(ragProperties.getSystemPromptTemplate())
                .as("System prompt template should be loaded in English and contain placeholders")
                .isNotBlank()
                .contains("{context}");

        assertThat(ragProperties.getFallbackMessage())
                .as("Fallback message should be loaded in English")
                .isNotBlank()
                .contains("could not find");

        assertThat(ragProperties.isIncludeSources())
                .as("Source inclusion flag should be explicitly mapped to true")
                .isTrue();

        // Performance parameters
        assertThat(ragProperties.getTimeout())
                .as("Timeout should be correctly parsed as 30 seconds")
                .isEqualTo(Duration.ofSeconds(30));

        // Log results using SLF4J
        log.info("SUCCESS: RAG Configuration loaded successfully from profile 'test-local'");
        log.info("SUCCESS: Loaded SystemPromptTemplate: {}", ragProperties.getSystemPromptTemplate());
        log.info("SUCCESS: Configured Timeout: {}s", ragProperties.getTimeout().toSeconds());
    }
}