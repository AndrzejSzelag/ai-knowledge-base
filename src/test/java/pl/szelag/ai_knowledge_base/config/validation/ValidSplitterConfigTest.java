package pl.szelag.ai_knowledge_base.config.validation;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import pl.szelag.ai_knowledge_base.config.SplitterProperties;

class ValidSplitterConfigTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("Should detect violations when @ValidSplitterConfig rules are broken")
    void validate_InvalidConfig_ReturnsViolations() {
        // GIVEN: Invalid configuration (overlap 450 > chunkSize 400)
        // All values are within @Max(512) to avoid standard validation noise
        SplitterProperties properties = new SplitterProperties();
        properties.setChunkSize(400);
        properties.setChunkOverlap(450);
        properties.setMinChunkSize(100);
        properties.setMaxChunkSize(512);

        // WHEN
        Set<ConstraintViolation<SplitterProperties>> violations = validator.validate(properties);

        // THEN
        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("chunkOverlap must be < chunkSize");
    }

    @Test
    @DisplayName("Should pass validation when all constraints (standard and custom) are met")
    void validate_ValidConfig_ReturnsNoViolations() {
        // GIVEN: Perfect configuration adhering to all @Min, @Max and
        // @ValidSplitterConfig rules
        SplitterProperties properties = new SplitterProperties();
        properties.setShortTextThreshold(200);
        properties.setChunkSize(320);
        properties.setChunkOverlap(50);
        properties.setMinChunkSize(100);
        properties.setMaxChunkSize(512);

        // WHEN
        Set<ConstraintViolation<SplitterProperties>> violations = validator.validate(properties);

        // THEN
        assertThat(violations).isEmpty();
    }
}