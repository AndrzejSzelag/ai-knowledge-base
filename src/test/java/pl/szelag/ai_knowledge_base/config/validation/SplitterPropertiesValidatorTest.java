package pl.szelag.ai_knowledge_base.config.validation;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintValidatorContext;
import pl.szelag.ai_knowledge_base.config.SplitterProperties;

class SplitterPropertiesValidatorTest {

    private SplitterPropertiesValidator validator;
    private ConstraintValidatorContext context;
    private ConstraintValidatorContext.ConstraintViolationBuilder builder;
    private ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext nodeBuilder;

    @BeforeEach
    void setUp() {
        validator = new SplitterPropertiesValidator();
        context = Mockito.mock(ConstraintValidatorContext.class);
        builder = Mockito.mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        nodeBuilder = Mockito
                .mock(ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext.class);

        // Mocking the fluent API of ConstraintValidatorContext
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addPropertyNode(anyString())).thenReturn(nodeBuilder);
    }

    @Test
    @DisplayName("Should return true when properties are null")
    void isValid_NullProperties_ReturnsTrue() {
        assertThat(validator.isValid(null, context)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "320, 250, 100, 500", // Valid config
            "320, , 100, 500" // Valid config with null overlap
    })
    @DisplayName("Should return true for valid configurations")
    void isValid_ValidConfigs_ReturnsTrue(int size, Integer overlap, int min, int max) {
        // GIVEN
        SplitterProperties p = createProperties(size, overlap, min, max);

        // WHEN & THEN
        assertThat(validator.isValid(p, context)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "300, 350, 100, 500, chunkOverlap must be < chunkSize",
            "450, 100, 100, 400, chunkSize must be <= maxChunkSize",
            "200, 50, 250, 500, minChunkSize must be <= chunkSize",
            "300, 50, 512, 400, minChunkSize must be <= maxChunkSize"
    })
    @DisplayName("Should return false and add violation when business rules are broken")
    void isValid_InvalidConfigs_ReturnsFalse(int size, Integer overlap, int min, int max, String expectedMessage) {
        // GIVEN
        SplitterProperties p = createProperties(size, overlap, min, max);

        // WHEN
        boolean result = validator.isValid(p, context);

        // THEN
        assertThat(result).isFalse();
        Mockito.verify(context).buildConstraintViolationWithTemplate(expectedMessage);
    }

    private SplitterProperties createProperties(int size, Integer overlap, int min, int max) {
        SplitterProperties p = new SplitterProperties();
        p.setChunkSize(size);
        p.setChunkOverlap(overlap);
        p.setMinChunkSize(min);
        p.setMaxChunkSize(max);
        return p;
    }
}