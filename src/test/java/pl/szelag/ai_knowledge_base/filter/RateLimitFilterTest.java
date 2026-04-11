package pl.szelag.ai_knowledge_base.filter;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        chain = mock(FilterChain.class);
    }

    // ── Routing ──────────────────────────────────────────────────────────────

    @Test
    void shouldPassThroughNonAiPaths() throws Exception {
        var req = request("/api/users/1", "1.1.1.1");
        var res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldRateLimitAiPaths() throws Exception {
        var req = request("/api/ai/chat", "2.2.2.2");

        for (int i = 0; i < 20; i++) {
            var res = new MockHttpServletResponse();
            filter.doFilterInternal(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }

    // ── Rate-limiting ─────────────────────────────────────────────────

    @Test
    void shouldReturn429AfterExceedingLimit() throws Exception {
        var req = request("/api/ai/chat", "3.3.3.3");

        for (int i = 0; i < 20; i++) {
            filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
        }

        var res = new MockHttpServletResponse();
        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(429);
        verify(chain, times(20)).doFilter(any(), any());
    }

    @Test
    void shouldIsolateBucketsPerIp() throws Exception {
        // IP A wyczerpuje limit
        var reqA = request("/api/ai/chat", "10.0.0.1");
        for (int i = 0; i < 20; i++) {
            filter.doFilterInternal(reqA, new MockHttpServletResponse(), chain);
        }

        // IP B nadal ma wolne tokeny
        var reqB = request("/api/ai/chat", "10.0.0.2");
        var resB = new MockHttpServletResponse();
        filter.doFilterInternal(reqB, resB, chain);

        assertThat(resB.getStatus()).isEqualTo(200);
    }

    // ── Headers ─────────────────────────────────────────────────────────────

    @Test
    void shouldDecreaseRateLimitRemainingHeader() throws Exception {
        var req = request("/api/ai/chat", "4.4.4.4");

        var res1 = new MockHttpServletResponse();
        filter.doFilterInternal(req, res1, chain);
        int remaining1 = Integer.parseInt(res1.getHeader("X-RateLimit-Remaining"));

        var res2 = new MockHttpServletResponse();
        filter.doFilterInternal(req, res2, chain);
        int remaining2 = Integer.parseInt(res2.getHeader("X-RateLimit-Remaining"));

        assertThat(remaining2).isLessThan(remaining1);
    }

    @Test
    void shouldReturnRetryAfterHeaderOn429() throws Exception {
        var req = request("/api/ai/chat", "5.5.5.5");
        for (int i = 0; i < 20; i++) {
            filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
        }

        var res = new MockHttpServletResponse();
        filter.doFilterInternal(req, res, chain);

        assertThat(res.getHeader("Retry-After")).isEqualTo("60");
        assertThat(res.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    void shouldReturnJsonBodyOn429() throws Exception {
        var req = request("/api/ai/chat", "6.6.6.6");
        for (int i = 0; i < 20; i++) {
            filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
        }

        var res = new MockHttpServletResponse();
        filter.doFilterInternal(req, res, chain);

        assertThat(res.getContentType()).contains("application/json");
        assertThat(res.getContentAsString()).contains("Too many requests");
    }

    // ── IP Validation ─────────────────────────────────────────────────────────

    @Test
    void shouldReturn400ForOversizedIp() throws Exception {
        String longIp = "A".repeat(46); // > 45 znaków
        var req = request("/api/ai/chat", longIp);
        var res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(400);
        verify(chain, never()).doFilter(any(), any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MockHttpServletRequest request(String uri, String remoteAddr) {
        var req = new MockHttpServletRequest("GET", uri);
        req.setRemoteAddr(remoteAddr);
        return req;
    }
}