package pl.szelag.ai_knowledge_base.dto;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Consolidated suite for Bean Validation constraint tests across all input
 * DTOs.
 * <p>
 * This class serves as the central point for verifying that business rules
 * (such as {@code @NotBlank} or {@code @NotNull}) are correctly enforced
 * on request payloads before they reach the service layer.
 */
class DtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        // GIVEN: Force US Locale to ensure validation messages are in English across
        // all environments
        Locale.setDefault(Locale.US);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "  ", "\t", "\n" })
    void shouldFailWhenAskRequestQuestionIsBlank(String invalidQuestion) {
        // GIVEN: An AskRequest with blank or whitespace-only question
        AskRequest request = new AskRequest(invalidQuestion);

        // WHEN: Validating the record
        var violations = validator.validate(request);

        // THEN: It should contain at least one constraint violation
        assertThat(violations).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "  " })
    void shouldFailWhenIngestRequestTextIsBlank(String invalidText) {
        // GIVEN: An IngestRequest with blank text
        IngestRequest request = new IngestRequest(invalidText);

        // WHEN: Validating the record
        var violations = validator.validate(request);

        // THEN: Validation should fail
        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldFailWhenIngestRequestTextIsNull() {
        // GIVEN: An IngestRequest with a null text field
        IngestRequest request = new IngestRequest(null);

        // WHEN: Validating the record
        var violations = validator.validate(request);

        // THEN: It should produce a violation
        assertThat(violations).isNotEmpty();
    }
}