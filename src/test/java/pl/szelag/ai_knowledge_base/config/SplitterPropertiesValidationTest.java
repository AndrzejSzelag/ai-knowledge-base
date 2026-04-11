package pl.szelag.ai_knowledge_base.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SplitterPropertiesValidationTest {

    private Validator validator;

    @BeforeEach
    void setup() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldFail_whenChunkSizeGreaterThanMax() {
        SplitterProperties p = new SplitterProperties();
        p.setChunkSize(500);
        p.setMaxChunkSize(400);
        p.setMinChunkSize(100);

        var violations = validator.validate(p);

        assertThat(violations)
                .anyMatch(v -> v.getMessage().contains("chunkSize"));
    }

    @Test
    void shouldNotFail_whenOverlapIsNull() {
        SplitterProperties p = new SplitterProperties();
        p.setChunkSize(300);
        p.setChunkOverlap(null); // important
        p.setMaxChunkSize(400);
        p.setMinChunkSize(100);

        var violations = validator.validate(p);

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldFail_whenOverlapGreaterThanChunkSize() {
        SplitterProperties p = new SplitterProperties();
        p.setChunkSize(100);
        p.setChunkOverlap(100);

        var violations = validator.validate(p);

        assertThat(violations)
                .anyMatch(v -> v.getMessage().contains("chunkOverlap"));
    }
}