package pl.szelag.ai_knowledge_base.config.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import pl.szelag.ai_knowledge_base.config.SplitterProperties;

/**
 * Validator for SplitterProperties.
 * Ensures:
 * - chunkOverlap < chunkSize (if overlap set)
 * - chunkSize <= maxChunkSize
 * - minChunkSize <= chunkSize
 * - minChunkSize <= maxChunkSize
 */
public class SplitterPropertiesValidator implements ConstraintValidator<ValidSplitterConfig, SplitterProperties> {

    /**
     * Implements the validation logic for SplitterProperties.
     */
    @Override
    public boolean isValid(SplitterProperties p, ConstraintValidatorContext ctx) {
        if (p == null) {
            return true; // null is handled by @NotNull; this validator only checks internal consistency
        }

        boolean valid = true;

        ctx.disableDefaultConstraintViolation();

        // Extract properties for easier comparison
        Integer overlap = p.getChunkOverlap();
        int chunkSize = p.getChunkSize();
        int min = p.getMinChunkSize();
        int max = p.getMaxChunkSize();

        // Check if overlap is smaller than the chunk size
        if (overlap != null && overlap >= chunkSize) {
            ctx.buildConstraintViolationWithTemplate("chunkOverlap must be < chunkSize")
                    .addPropertyNode("chunkOverlap")
                    .addConstraintViolation();
            valid = false;
        }

        // Check if current chunk size does not exceed the maximum allowed
        if (chunkSize > max) {
            ctx.buildConstraintViolationWithTemplate("chunkSize must be <= maxChunkSize")
                    .addPropertyNode("chunkSize")
                    .addConstraintViolation();
            valid = false;
        }

        // Check if minimum chunk size is not greater than the current chunk size
        if (min > chunkSize) {
            ctx.buildConstraintViolationWithTemplate("minChunkSize must be <= chunkSize")
                    .addPropertyNode("minChunkSize")
                    .addConstraintViolation();
            valid = false;
        }

        // Check the logical consistency between min and max chunk sizes
        if (min > max) {
            ctx.buildConstraintViolationWithTemplate("minChunkSize must be <= maxChunkSize")
                    .addPropertyNode("minChunkSize")
                    .addConstraintViolation();
            valid = false;
        }

        return valid;
    }
}