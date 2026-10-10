package com.project.gatekeeper.gateway.proxy;

import com.project.gatekeeper.gateway.support.FlatJson;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class IdempotencyService {

    static final int MAX_STORED_BYTES = 262144;

    private final StringRedisTemplate redis;

    public IdempotencyService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Result begin(String routeId, String key) {
        String redisKey = redisKey(routeId, key);
        try {
            Boolean created = redis.opsForValue().setIfAbsent(redisKey, "INFLIGHT", Duration.ofSeconds(60));
            if (Boolean.TRUE.equals(created)) {
                return new Result.Start();
            }
            String existing = redis.opsForValue().get(redisKey);
            if (existing == null || "INFLIGHT".equals(existing)) {
                return new Result.Busy();
            }
            Map<String, String> stored = FlatJson.read(existing);
            byte[] body = Base64.getDecoder().decode(stored.getOrDefault("body", ""));
            return new Result.Replay(Integer.parseInt(stored.get("status")), stored.get("contentType"), body);
        } catch (RuntimeException ex) {
            throw new RateLimitService.RateLimiterUnavailableException(ex);
        }
    }

    public void complete(String routeId, String key, int status, String contentType, byte[] body) {
        String redisKey = redisKey(routeId, key);
        if (status >= 500 || body.length > MAX_STORED_BYTES) {
            redis.delete(redisKey);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("contentType", contentType == null ? "application/octet-stream" : contentType);
        payload.put("body", Base64.getEncoder().encodeToString(body));
        redis.opsForValue().set(redisKey, FlatJson.write(payload), Duration.ofHours(24));
    }

    private String redisKey(String routeId, String key) {
        return "idem:" + routeId + ":" + key;
    }

    public sealed interface Result permits Result.Replay, Result.Start, Result.Busy {
        record Replay(int status, String contentType, byte[] body) implements Result {
        }

        record Start() implements Result {
        }

        record Busy() implements Result {
        }
    }
}
