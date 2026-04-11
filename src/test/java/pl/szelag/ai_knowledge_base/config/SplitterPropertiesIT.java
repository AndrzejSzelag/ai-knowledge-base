package pl.szelag.ai_knowledge_base.config;

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
 * Integration test for SplitterProperties configuration.
 * Verifies that text splitting parameters are correctly bound from the
 * environment
 * and that the effective overlap calculation logic remains consistent.
 */
@Slf4j
@SpringJUnitConfig(initializers = ConfigDataApplicationContextInitializer.class)
@EnableConfigurationProperties(SplitterProperties.class)
@ActiveProfiles("test-local")
class SplitterPropertiesIT {

    @Autowired
    private SplitterProperties properties;

    @Test
    @DisplayName("Should bind properties and compute effective overlap correctly")
    void shouldBindAndComputeEffectiveOverlap() {
        // GIVEN: Application context is initialized with 'test-local' profile

        // WHEN + THEN: Verify basic size constraints
        assertThat(properties.getChunkSize())
                .as("Base chunk size must be a positive integer")
                .isPositive();

        // Calculate effective overlap based on current properties
        int overlap = properties.getEffectiveChunkOverlap();

        // Verify that overlap is within valid bounds
        assertThat(overlap)
                .as("Overlap must be non-negative and smaller than the total chunk size")
                .isGreaterThanOrEqualTo(0)
                .isLessThan(properties.getChunkSize());

        // Verify chunk size range consistency
        assertThat(properties.getMaxChunkSize())
                .as("Maximum chunk size must be greater than minimum chunk size")
                .isGreaterThan(properties.getMinChunkSize());

        // Logic check: Verify if the overlap is correctly derived from the ratio when
        // no fixed overlap is set
        if (properties.getChunkOverlap() == null) {
            int expected = (int) Math.round(
                    properties.getChunkSize() * properties.getOverlapRatio());

            assertThat(overlap)
                    .as("Effective overlap should match the calculated ratio when fixed overlap is null")
                    .isEqualTo(expected);
        }

        // Log the final calculated state for debugging
        log.info("SUCCESS: Verified SplitterProperties: ChunkSize={}, EffectiveOverlap={}",
                properties.getChunkSize(), overlap);
    }
}