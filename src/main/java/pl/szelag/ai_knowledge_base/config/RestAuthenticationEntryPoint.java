package pl.szelag.ai_knowledge_base.config;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Custom authentication entry point for managing unauthenticated access
 * attempts.
 * This component distinguishes between API calls and web requests to provide
 * appropriate responses (401 Unauthorized vs. Redirect).
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    // Prefix identifying API-based requests to be handled with error codes
    private static final String API_PREFIX = "/api/";

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        // Determine the response strategy based on the request URI
        if (request.getRequestURI().startsWith(API_PREFIX)) {
            // Return 401 Unauthorized for API endpoints to prevent default browser login
            // prompts
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
        } else {
            // Redirect non-API requests to the landing page (triggers OAuth2 login flow)
            response.sendRedirect("/");
        }
    }
}