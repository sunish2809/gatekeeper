package com.project.gatekeeper.management.security;

import com.project.gatekeeper.management.service.AuthService.AuthResult;
import com.project.gatekeeper.management.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OidcLoginCodes {

    private static final Duration TTL = Duration.ofMinutes(2);
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public String issue(AuthResult result) {
        pending.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(Instant.now()));
        byte[] value = new byte[32];
        random.nextBytes(value);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        pending.put(code, new Pending(result, Instant.now().plus(TTL)));
        return code;
    }

    public AuthResult consume(String code) {
        Pending found = code == null ? null : pending.remove(code);
        if (found == null || found.expiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "oidc_code_invalid",
                    "Google sign-in expired or was already used. Please try again.");
        }
        return found.result();
    }

    private record Pending(AuthResult result, Instant expiresAt) {
    }
}
