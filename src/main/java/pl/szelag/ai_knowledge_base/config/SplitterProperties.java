package pl.szelag.ai_knowledge_base.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import pl.szelag.ai_knowledge_base.config.validation.ValidSplitterConfig;

/**
 * Configuration properties for document chunking (prefix: app.splitter).
 * 
 * Example values per model:
 * - llama3.2:1b: shortTextThreshold=200, chunkSize=320, chunkOverlap=70,
 * minChunkSize=120, maxChunkSize=420
 * - llama3.2:3b: shortTextThreshold=250, chunkSize=350, chunkOverlap=60,
 * minChunkSize=80, maxChunkSize=450
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.splitter")
@Validated
@ValidSplitterConfig
public class SplitterProperties {

    // Texts shorter than this are not split into chunks.
    @Min(0)
    private int shortTextThreshold;

    /** Target size of each chunk in characters. */
    @Positive
    @Max(512)
    private int chunkSize;

    /**
     * Optional manual overlap override.
     * If null, overlap is computed from {@link #overlapRatio}.
     */
    @Min(0)
    private Integer chunkOverlap;

    /**
     * Used only when chunkOverlap == null e.g. 0.2 = 20%
     */
    @DecimalMin("0.0")
    @DecimalMax("0.5")
    private double overlapRatio = 0.2;

    // Minimum allowed chunk size; smaller chunks are discarded.
    @Min(1)
    private int minChunkSize;

    // Maximum allowed chunk size.
    @Max(512)
    private int maxChunkSize;

    /**
     * Final overlap used by the system.
     */
    public int getEffectiveChunkOverlap() {
        if (chunkOverlap != null) {
            return chunkOverlap;
        }
        return (int) Math.round(chunkSize * overlapRatio);
    }

}