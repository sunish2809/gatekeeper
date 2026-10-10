package com.project.gatekeeper.gateway.proxy;

import com.project.gatekeeper.gateway.snapshot.GatewayModels.RouteRule;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class RateLimitService {

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Decision check(RouteRule route, String apiKey) {
        if (route.rateLimit() <= 0) {
            return Decision.unlimited();
        }
        long window = route.rateWindowSeconds();
        long bucket = Instant.now().getEpochSecond() / window;
        String identity = identity(apiKey);
        String key = "rl:" + route.routeId() + ":" + identity + ":" + bucket;
        try {
            Long count = redis.opsForValue().increment(key);
            long used = count == null ? 1 : count;
            if (used == 1) {
                redis.expire(key, Duration.ofSeconds(window));
            }
            if (used > route.rateLimit()) {
                return new Decision(false, route.rateLimit(), 0, (int) window);
            }
            return new Decision(true, route.rateLimit(), Math.max(0, route.rateLimit() - used), (int) window);
        } catch (RuntimeException ex) {
            throw new RateLimiterUnavailableException(ex);
        }
    }

    public record Decision(boolean allowed, int limit, long remaining, int retryAfterSeconds) {
        public static Decision unlimited() {
            return new Decision(true, 0, -1, 0);
        }
    }

    public static class RateLimiterUnavailableException extends RuntimeException {
        public RateLimiterUnavailableException(Throwable cause) {
            super(cause);
        }
    }

    private static String identity(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "public";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (Exception ex) {
            return "key";
        }
    }
}
