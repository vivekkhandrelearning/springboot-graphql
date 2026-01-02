package com.example.dynamicgraphreportui;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RateLimitFilter implements Filter {

    private final RateLimitingService rateLimitingService;

    public RateLimitFilter(RateLimitingService rateLimitingService) {
        this.rateLimitingService = rateLimitingService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
            
            // Only apply to GraphQL endpoints
            String path = httpRequest.getRequestURI();
            if (path.contains("/graphql")) {
                
                String ip = getClientIP(httpRequest);
                Bucket bucket = rateLimitingService.resolveBucket(ip);
                ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

                if (probe.isConsumed()) {
                    httpResponse.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
                    chain.doFilter(request, response);
                } else {
                    httpResponse.setStatus(429);
                    httpResponse.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(probe.getNanosToWaitForRefill() / 1_000_000_000));
                    httpResponse.setContentType("application/json");
                    httpResponse.getWriter().write("{\"error\": \"Too many requests\"}");
                }
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
