package in.rithik.resumebuilderapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
Purpose:
Intercepts auth and file upload HTTP requests to enforce rate-limiting via Token Bucket.

Used By:
Spring Security Filter Chain

Request Flow:
Client Request -> RateLimitingFilter -> Security Filters -> Controller

Learn:
- Spring OncePerRequestFilter
- Token Bucket Rate Limiting Algorithm
- ConcurrentHashMap usage
*/
@Component
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    private static class TokenBucket {
        private final double capacity;
        private final double refillRatePerSecond;
        private double tokens;
        private long lastRefillTime;

        public TokenBucket(double capacity, double refillRatePerSecond) {
            this.capacity = capacity;
            this.refillRatePerSecond = refillRatePerSecond;
            this.tokens = capacity;
            this.lastRefillTime = System.currentTimeMillis();
        }

        public synchronized boolean tryConsume() {
            long now = System.currentTimeMillis();
            double elapsed = (now - lastRefillTime) / 1000.0;
            lastRefillTime = now;
            tokens = Math.min(capacity, tokens + elapsed * refillRatePerSecond);
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();
        
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }

        String endpointType = getEndpointType(path, method);

        if (endpointType != null) {
            String bucketKey = endpointType + ":" + ip;
            TokenBucket bucket = buckets.computeIfAbsent(bucketKey, k -> createBucketForType(endpointType));

            if (!bucket.tryConsume()) {
                log.warn("Rate limit exceeded for IP {} on endpoint {} ({})", ip, path, endpointType);
                sendTooManyRequestsResponse(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getEndpointType(String path, String method) {
        if ("POST".equalsIgnoreCase(method)) {
            if (path.equals("/api/auth/login")) {
                return "LOGIN";
            } else if (path.equals("/api/auth/register")) {
                return "REGISTER";
            } else if (path.equals("/api/ai/rewrite")) {
                return "AI_REWRITE";
            }
        }
        if (("POST".equalsIgnoreCase(method) && path.equals("/api/auth/upload-image")) ||
            ("PUT".equalsIgnoreCase(method) && path.matches("/api/resumes/[^/]+/upload-images"))) {
            return "UPLOADS";
        }
        return null;
    }

    private TokenBucket createBucketForType(String type) {
        return switch (type) {
            case "LOGIN" -> new TokenBucket(5.0, 5.0 / 60.0);       // Max 5, refill 1 every 12 seconds
            case "REGISTER" -> new TokenBucket(3.0, 3.0 / 60.0);    // Max 3, refill 1 every 20 seconds
            case "AI_REWRITE" -> new TokenBucket(10.0, 10.0 / 60.0); // Max 10, refill 1 every 6 seconds
            case "UPLOADS" -> new TokenBucket(5.0, 5.0 / 60.0);     // Max 5, refill 1 every 12 seconds
            default -> new TokenBucket(20.0, 20.0 / 60.0);
        };
    }

    private void sendTooManyRequestsResponse(HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());

        Map<String, String> errorResponse = Map.of(
                "message", "Too many requests. Please try again shortly.",
                "error", "Rate limit exceeded"
        );

        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
