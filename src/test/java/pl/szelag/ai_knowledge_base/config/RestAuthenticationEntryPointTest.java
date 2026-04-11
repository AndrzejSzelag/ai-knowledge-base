package pl.szelag.ai_knowledge_base.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Unit tests for RestAuthenticationEntryPoint logic.
 */
class RestAuthenticationEntryPointTest {

    private final RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint();
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void shouldReturn401ForApiRequest() throws Exception {
        // GIVEN
        request.setRequestURI("/api/test");

        // WHEN
        entryPoint.commence(request, response, mockAuthException());

        // THEN
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void shouldRedirectToRootForNonApiRequest() throws Exception {
        // GIVEN
        request.setRequestURI("/home");

        // WHEN
        entryPoint.commence(request, response, mockAuthException());

        // THEN
        // SC_FOUND represents 302 Redirect
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FOUND);
        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void shouldReturn401ForApiSubpath() throws Exception {
        request.setRequestURI("/api/users/123/profile");

        entryPoint.commence(request, response, mockAuthException());

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void shouldReturn401WhenUriIsExactlyApiPrefix() throws Exception {
        request.setRequestURI("/api/");

        entryPoint.commence(request, response, mockAuthException());

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    /**
     * Helper method to create a generic AuthenticationException for testing.
     */
    private AuthenticationException mockAuthException() {
        return new AuthenticationException("Unauthorized access") {
        };
    }
}