package pl.szelag.ai_knowledge_base.controller;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import org.springframework.test.context.TestPropertySource;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import pl.szelag.ai_knowledge_base.config.RestAuthenticationEntryPoint;
import pl.szelag.ai_knowledge_base.config.SecurityConfig;

/**
 * Integration tests for AuthController focusing on OAuth2 profile mapping.
 * Uses BaseWebMvcTest to inherit security configuration and MockMvc instance.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class})
@TestPropertySource(properties = "app.frontend-url=http://localhost:3000")
class AuthControllerIT extends BaseWebMvcTest {

        @Test
        void getMe_authenticatedOAuth2User_returns200WithProfile() throws Exception {
                // GIVEN
                // Authorities are a technical requirement for DefaultOAuth2User,
                // though not used by the controller logic itself.
                OAuth2User oAuth2User = new DefaultOAuth2User(
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                                Map.of(
                                                "name", "Andrzej Szelag",
                                                "email", "andrzej.szelag@gmail.com",
                                                "picture", "https://lh3.googleusercontent.com/a/photo.jpg",
                                                "given_name", "Andrzej",
                                                "family_name", "Szelag",
                                                "sub", "google-id-12345"),
                                "sub");

                // WHEN + THEN
                mockMvc.perform(get("/api/auth/me")
                                .with(oauth2Login().oauth2User(oAuth2User)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value("Andrzej Szelag"))
                                .andExpect(jsonPath("$.email").value("andrzej.szelag@gmail.com"))
                                .andExpect(jsonPath("$.picture").value("https://lh3.googleusercontent.com/a/photo.jpg"))
                                .andExpect(jsonPath("$.givenName").value("Andrzej"))
                                .andExpect(jsonPath("$.familyName").value("Szelag"));
        }

        @Test
        void getMe_unauthenticated_returns401Unauthorized() throws Exception {
                // GIVEN:
                // Unauthenticated request

                // WHEN + THEN
                mockMvc.perform(get("/api/auth/me"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void getMe_missingOptionalAttributes_returnsEmptyStrings() throws Exception {
                // GIVEN
                // Simulating a provider that only returns a unique identifier (sub)
                OAuth2User oAuth2User = new DefaultOAuth2User(
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                                Map.of("sub", "google-id-99999"),
                                "sub");

                // WHEN + THEN
                mockMvc.perform(get("/api/auth/me")
                                .with(oauth2Login().oauth2User(oAuth2User)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value(""))
                                .andExpect(jsonPath("$.email").value(""))
                                .andExpect(jsonPath("$.picture").value(""))
                                .andExpect(jsonPath("$.givenName").value(""))
                                .andExpect(jsonPath("$.familyName").value(""));
        }
}