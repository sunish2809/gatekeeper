package com.project.gatekeeper.gateway.snapshot;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GatewayModels {

    private GatewayModels() {
    }

    public record SnapshotResponse(List<ClientSnapshot> clients) {
    }

    public record ClientSnapshot(
            String id,
            String name,
            String apiKey,
            boolean active,
            List<String> roles,
            List<RouteSnapshot> routes,
            String adminId
    ) {
    }

    public record Caller(String clientId, String adminId, Set<String> roles) {
    }

    public record RouteSnapshot(
            String id,
            String publicPath,
            String targetUrl,
            List<String> methods,
            int timeoutMs,
            boolean authRequired,
            List<String> allowedRoles,
            int rateLimit,
            int rateWindowSeconds,
            boolean idempotentPost,
            boolean stripPrefix,
            Map<String, String> requestHeaders,
            boolean enabled,
            boolean requireUserJwt
    ) {
    }

    public record RouteRule(
            String routeId,
            String clientId,
            String apiKey,
            String adminId,
            String publicPath,
            String targetUrl,
            Set<String> methods,
            int timeoutMs,
            boolean authRequired,
            Set<String> allowedRoles,
            int rateLimit,
            int rateWindowSeconds,
            boolean idempotentPost,
            boolean stripPrefix,
            Map<String, String> requestHeaders,
            boolean requireUserJwt
    ) {
    }

    public record Match(RouteRule route, boolean methodNotAllowed) {
        public static Match found(RouteRule route) {
            return new Match(route, false);
        }

        public static Match methodRejected() {
            return new Match(null, true);
        }

        public static Match none() {
            return new Match(null, false);
        }
    }
}
