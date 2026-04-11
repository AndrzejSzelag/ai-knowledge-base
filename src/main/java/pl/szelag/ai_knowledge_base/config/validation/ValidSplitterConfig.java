package pl.szelag.ai_knowledge_base.config.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotation for validating the consistency of splitter configuration.
 * <p>
 * Checks that chunk sizes and overlap are internally consistent.
 * Applied at the class level on {@link pl.szelag.ai_knowledge_base.config.SplitterProperties}.
 * </p>
 * 
 * @see SplitterPropertiesValidator
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SplitterPropertiesValidator.class)
@Documented
public @interface ValidSplitterConfig {

    /** The default error message when validation fails. */
    String message() default "Invalid splitter configuration";

    /** The groups the constraint belongs to. */
    Class<?>[] groups() default {};

    /** Additional metadata assigned to the constraint. */
    Class<? extends Payload>[] payload() default {};
}