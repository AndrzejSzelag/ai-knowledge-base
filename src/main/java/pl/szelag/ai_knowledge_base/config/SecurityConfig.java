package pl.szelag.ai_knowledge_base.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Configures Spring Security: OAuth2, CSRF, CORS, and session management.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

        private static final String[] PUBLIC_AI_ENDPOINTS = {
                        "/api/ai/ask",
                        "/api/ai/ask-streaming"
        };

        private static final String[] PUBLIC_STATIC_RESOURCES = {
                        "/", "/index.html", "/favicon.ico",
                        "/assets/**", "/static/**", "/*.js", "/*.css", "/manifest.json"
        };

        private static final String[] PUBLIC_AUTH_ENDPOINTS = {
                        "/api/auth/**", "/oauth2/**", "/login/oauth2/**"
        };

        private static final String[] PROTECTED_KNOWLEDGE_ENDPOINTS = {
                        "/api/knowledge/**",
                        "/api/documents/**"
        };

        private final String frontendUrl;

        private final RestAuthenticationEntryPoint authenticationEntryPoint;

        public SecurityConfig(
                        @Value("${app.frontend-url}") String frontendUrl,
                        RestAuthenticationEntryPoint authenticationEntryPoint) {
                this.frontendUrl = frontendUrl;
                this.authenticationEntryPoint = authenticationEntryPoint;
        }

        /** Builds the security filter chain with auth rules and handlers. */
        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

                CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
                requestHandler.setCsrfRequestAttributeName(null);

                http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                                .csrf(csrf -> csrf
                                                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                                                .csrfTokenRequestHandler(requestHandler)
                                                .ignoringRequestMatchers(PUBLIC_AI_ENDPOINTS)
                                                .ignoringRequestMatchers(PUBLIC_STATIC_RESOURCES))

                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint(authenticationEntryPoint))

                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(PUBLIC_STATIC_RESOURCES).permitAll()
                                                .requestMatchers(PUBLIC_AUTH_ENDPOINTS).permitAll()
                                                .requestMatchers(PUBLIC_AI_ENDPOINTS).permitAll()
                                                .requestMatchers(PROTECTED_KNOWLEDGE_ENDPOINTS).authenticated()
                                                .anyRequest().authenticated())

                                .oauth2Login(oauth2 -> oauth2
                                                .loginPage("/")
                                                .successHandler(oauth2SuccessHandler())
                                                .failureHandler(oauth2FailureHandler()))

                                .logout(logout -> logout
                                                .logoutUrl("/api/auth/logout")
                                                .logoutSuccessHandler((req, res, auth) -> res
                                                                .setStatus(HttpServletResponse.SC_OK))
                                                .invalidateHttpSession(true)
                                                .deleteCookies("JSESSIONID", "XSRF-TOKEN"))

                                .sessionManagement(s -> s
                                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                                                .sessionFixation(sf -> sf.migrateSession()))

                                .headers(h -> h
                                                .frameOptions(f -> f.deny())
                                                .contentTypeOptions(c -> {
                                                }) // X-Content-Type-Options: nosniff
                                                .httpStrictTransportSecurity(hsts -> hsts
                                                                .includeSubDomains(true)
                                                                .maxAgeInSeconds(31536000))
                                                .referrerPolicy(r -> r
                                                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));

                return http.build();
        }

        /** Redirects to frontend root after successful OAuth2 login. */
        @Bean
        public SimpleUrlAuthenticationSuccessHandler oauth2SuccessHandler() {
                SimpleUrlAuthenticationSuccessHandler handler = new SimpleUrlAuthenticationSuccessHandler();
                handler.setDefaultTargetUrl(frontendUrl + "/");
                handler.setAlwaysUseDefaultTargetUrl(true);
                return handler;
        }

        /** Redirects to login page with error flag on OAuth2 failure. */
        @Bean
        public SimpleUrlAuthenticationFailureHandler oauth2FailureHandler() {
                return new SimpleUrlAuthenticationFailureHandler(frontendUrl + "/login?error=true");
        }

        /** CORS config for frontend (credentials + required headers). */
        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration config = new CorsConfiguration();
                config.setAllowedOrigins(List.of(frontendUrl));
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                config.setAllowedHeaders(List.of(
                                "Authorization",
                                "Cache-Control",
                                "Content-Type",
                                "X-XSRF-TOKEN",
                                "Last-Event-ID"));
                config.setAllowCredentials(true);
                // cache preflight for 1 hour
                config.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", config);
                return source;
        }
}