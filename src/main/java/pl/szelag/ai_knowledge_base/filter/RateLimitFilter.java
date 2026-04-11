package pl.szelag.ai_knowledge_base.filter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Token-bucket rate limiter (Bucket4j) for {@code /api/ai/*} endpoints.
 * Limits each client IP to {@value REQUESTS_PER_MINUTE} req/min.
 * Responds with 429 + {@code Retry-After} / {@code X-RateLimit-*} headers when exceeded.
 * Bucket map is cleared hourly to prevent memory leaks.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int REQUESTS_PER_MINUTE = 20;
    private static final String RATE_LIMITED_PATH_PREFIX = "/api/ai/";

    /** Per-IP token buckets; cleared hourly to avoid unbounded growth. */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    /** Schedules hourly bucket map cleanup. */
    public RateLimitFilter() {
        cleaner.scheduleAtFixedRate(buckets::clear, 1, 1, TimeUnit.HOURS);
    }

    /** Rate-limits {@code /api/ai/*} requests; all other paths pass through. */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith(RATE_LIMITED_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);

        // Reject oversized keys — max legitimate IPv6 length is 45 chars.
        if (ip.length() > 45) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return;
        }

        Bucket bucket = buckets.computeIfAbsent(ip, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(REQUESTS_PER_MINUTE)
                        .refillGreedy(REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
                        .build())
                .build());

        if (bucket.tryConsume(1)) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(REQUESTS_PER_MINUTE));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            chain.doFilter(request, response);
        } else {
            // RFC 6585 §4 — 429 with retry hint.
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", "60");
            response.setHeader("X-RateLimit-Limit", String.valueOf(REQUESTS_PER_MINUTE));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.getWriter().write("""
                    {"error":"Too many requests","retryAfter":60}
                    """);
        }
    }

    /**
     * Resolves client IP from {@code remoteAddr}.
     * Behind a reverse proxy, switch to {@code X-Forwarded-For} (ensure proxy is trusted).
     */
    private String getClientIp(HttpServletRequest request) {
        // Behind a trusted proxy, replace with:
        // String forwarded = request.getHeader("X-Forwarded-For");
        // if (forwarded != null && !forwarded.isBlank()) return
        // forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    /** Shuts down the cleanup scheduler to prevent thread leaks. */
    @Override
    public void destroy() {
        cleaner.shutdownNow();
        super.destroy();
    }
}