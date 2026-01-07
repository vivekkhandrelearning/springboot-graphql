package com.example.dynamicgraphreportui.filter;

import java.io.PrintWriter;

import com.example.dynamicgraphreportui.service.RateLimitingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RateLimitingService rateLimitingService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;
    @Mock
    private Bucket bucket;
    @Mock
    private ConsumptionProbe probe;

    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        rateLimitFilter = new RateLimitFilter(rateLimitingService);
    }

    @Test
    void doFilter_NonGraphqlRequest_ShouldProceed() throws Exception {
        when(request.getRequestURI()).thenReturn("/health");

        rateLimitFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(rateLimitingService, never()).resolveBucket(anyString());
    }

    @Test
    void doFilter_GraphqlRequest_WithinLimit_ShouldProceed() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/graphql");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(rateLimitingService.resolveBucket("127.0.0.1")).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(9L);

        rateLimitFilter.doFilter(request, response, filterChain);

        verify(response).setHeader("X-Rate-Limit-Remaining", "9");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_GraphqlRequest_ExceededLimit_ShouldReturn429() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/graphql");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(rateLimitingService.resolveBucket("127.0.0.1")).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(false);
        when(probe.getNanosToWaitForRefill()).thenReturn(3000000000L);
        when(response.getWriter()).thenReturn(mock(PrintWriter.class));

        rateLimitFilter.doFilter(request, response, filterChain);

        verify(response).setStatus(429);
        verify(response).setHeader("X-Rate-Limit-Retry-After-Seconds", "3");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilter_UsesXForwardedFor_WhenPresent() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/graphql");
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 192.168.1.1");
        when(rateLimitingService.resolveBucket("10.0.0.1")).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true);

        rateLimitFilter.doFilter(request, response, filterChain);

        verify(rateLimitingService).resolveBucket("10.0.0.1");
    }
}
