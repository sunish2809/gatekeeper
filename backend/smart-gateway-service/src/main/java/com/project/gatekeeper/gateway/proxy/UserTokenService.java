package com.project.gatekeeper.gateway.proxy;

import com.project.gatekeeper.gateway.snapshot.GatewayModels.RouteRule;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class UserTokenService {

    public enum Status {
        SKIP, OK, INVALID, UNAVAILABLE
    }

    public record Outcome(Status status, String subject) {
        static Outcome skip() {
            return new Outcome(Status.SKIP, null);
        }

        static Outcome ok(String subject) {
            return new Outcome(Status.OK, subject);
        }

        static Outcome invalid() {
            return new Outcome(Status.INVALID, null);
        }

        static Outcome unavailable() {
            return new Outcome(Status.UNAVAILABLE, null);
        }
    }

    private final CallerJwt callerJwt;
    private final ConcurrentMap<String, JwtDecoder> decoders = new ConcurrentHashMap<>();

    public UserTokenService(CallerJwt callerJwt) {
        this.callerJwt = callerJwt;
    }

    public Outcome verify(RouteRule route, String authorization) {
        boolean oidc = route.oidcIssuer() != null && !route.oidcIssuer().isBlank();
        if (oidc) {
            return verifyOidc(route, authorization);
        }
        if (route.requireUserJwt()) {
            String subject = callerJwt.subject(authorization);
            return subject == null ? Outcome.invalid() : Outcome.ok(subject);
        }
        String subject = callerJwt.subject(authorization);
        return subject == null ? Outcome.skip() : Outcome.ok(subject);
    }

    private Outcome verifyOidc(RouteRule route, String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Outcome.invalid();
        }
        String token = authorization.substring(7).trim();
        JwtDecoder decoder;
        try {
            decoder = decoderFor(route);
        } catch (RuntimeException ex) {
            return Outcome.unavailable();
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException ex) {
            return Outcome.invalid();
        }
        if (!hasRequiredScopes(jwt, route.requiredScopes())) {
            return Outcome.invalid();
        }
        String subject = jwt.getSubject();
        return subject == null || subject.isBlank() ? Outcome.invalid() : Outcome.ok(subject);
    }

    private JwtDecoder decoderFor(RouteRule route) {
        String key = route.oidcIssuer() + "\n" + route.oidcAudience();
        return decoders.computeIfAbsent(key, ignored -> buildDecoder(route.oidcIssuer(), route.oidcAudience()));
    }

    private JwtDecoder buildDecoder(String issuer, String audience) {
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuer);
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> withAudience = jwt -> {
            if (audience != null && jwt.getAudience() != null && jwt.getAudience().contains(audience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "audience does not match", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }

    static boolean hasRequiredScopes(Jwt jwt, Set<String> required) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        Set<String> present = scopes(jwt);
        return present.containsAll(required);
    }

    static Set<String> scopes(Jwt jwt) {
        Set<String> values = new LinkedHashSet<>();
        addScopes(values, jwt.getClaim("scope"));
        addScopes(values, jwt.getClaim("scp"));
        addScopes(values, jwt.getClaim("roles"));
        return values;
    }

    private static void addScopes(Set<String> values, Object claim) {
        if (claim == null) {
            return;
        }
        if (claim instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null && !item.toString().isBlank()) {
                    values.add(item.toString().trim());
                }
            }
            return;
        }
        for (String part : claim.toString().split("[\\s,]+")) {
            if (!part.isBlank()) {
                values.add(part.trim());
            }
        }
    }
}
