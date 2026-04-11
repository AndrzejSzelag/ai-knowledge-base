package pl.szelag.ai_knowledge_base.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;
import pl.szelag.ai_knowledge_base.dto.UserProfileResponse;

/**
 * Handles authentication-related endpoints.
 * <p>
 * Exposes the current user's OAuth2 profile so the frontend can display
 * account information without making a separate call to the identity provider.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * Returns the profile of the authenticated OAuth2 user, or 401 if not
     * authenticated.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal OAuth2User user) {
        if (user == null) {
            log.warn("Unauthenticated access attempt to /api/auth/me");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.debug("Returning profile for user: {}", nullSafe(user.getAttribute("email")));
        return ResponseEntity.ok(new UserProfileResponse(
                nullSafe(user.getAttribute("name")),
                nullSafe(user.getAttribute("email")),
                nullSafe(user.getAttribute("picture")),
                nullSafe(user.getAttribute("given_name")),
                nullSafe(user.getAttribute("family_name"))));
    }

    /** Returns value.toString() or empty string if null. */
    private String nullSafe(Object value) {
        return value != null ? value.toString() : "";
    }
}