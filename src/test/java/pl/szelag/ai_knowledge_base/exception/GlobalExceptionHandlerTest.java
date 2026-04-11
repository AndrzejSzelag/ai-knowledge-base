package pl.szelag.ai_knowledge_base.exception;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ── handleValidation ─────────────────────────────────────────────────────

    @Test
    void handleValidation_singleFieldError_returns400WithDetails() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("obj", "text", "must not be blank");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ProblemDetail result = handler.handleValidation(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getTitle()).isEqualTo("Validation failed");
        assertThat(result.getDetail()).isEqualTo("text: must not be blank");
        assertThat(result.getType().toString()).isEqualTo("https://api.error/validation-error");
    }

    @Test
    void handleValidation_multipleFieldErrors_returnsCommaSeparated() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError error1 = new FieldError("obj", "text", "must not be blank");
        FieldError error2 = new FieldError("obj", "title", "must not be null");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(error1, error2));

        ProblemDetail result = handler.handleValidation(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getDetail()).contains("text: must not be blank");
        assertThat(result.getDetail()).contains("title: must not be null");
    }

    @Test
    void handleValidation_noFieldErrors_returnsEmptyDetail() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());

        ProblemDetail result = handler.handleValidation(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getDetail()).isEmpty();
    }

    // ── handleIllegalArgument ─────────────────────────────────────────────────

    @Test
    void handleIllegalArgument_returns400WithMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("invalid UUID format");

        ProblemDetail result = handler.handleIllegalArgument(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getTitle()).isEqualTo("Invalid request parameter");
        assertThat(result.getDetail()).isEqualTo("invalid UUID format");
        assertThat(result.getType().toString()).isEqualTo("https://api.error/invalid-argument");
    }

    @Test
    void handleIllegalArgument_nullMessage_handledGracefully() {
        IllegalArgumentException ex = new IllegalArgumentException((String) null);

        ProblemDetail result = handler.handleIllegalArgument(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getDetail()).isNull();
    }

    // ── handleGeneral ─────────────────────────────────────────────────────────

    @Test
    void handleGeneral_returns500WithGenericMessage() {
        Exception ex = new RuntimeException("database connection lost");

        ProblemDetail result = handler.handleGeneral(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(result.getTitle()).isEqualTo("Internal server error");
        assertThat(result.getDetail()).isEqualTo("Service unavailable. Please try again.");
        assertThat(result.getType().toString()).isEqualTo("https://api.error/internal-error");
    }

    @Test
    void handleGeneral_nullMessageException_returns500() {
        Exception ex = new RuntimeException((String) null);

        ProblemDetail result = handler.handleGeneral(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(result.getDetail()).isEqualTo("Service unavailable. Please try again.");
    }

    @Test
    void handleGeneral_checkedExceptionSubclass_returns500() {
        Exception ex = new Exception("unexpected checked exception");

        ProblemDetail result = handler.handleGeneral(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(result.getType().toString()).isEqualTo("https://api.error/internal-error");
    }
}