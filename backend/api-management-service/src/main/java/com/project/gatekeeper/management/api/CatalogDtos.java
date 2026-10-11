package com.project.gatekeeper.management.api;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CreateClientRequest(
            @NotBlank String name,
            String apiKey,
            List<String> roles
    ) {
    }

    public record ClientResponse(
            String id,
            String name,
            String apiKey,
            boolean active,
            List<String> roles,
            Instant createdAt
    ) {
    }

    public record UpsertRouteRequest(
            @NotBlank String name,
            @NotBlank String publicPath,
            @NotBlank String targetUrl,
            List<String> methods,
            Integer timeoutMs,
            Boolean authRequired,
            List<String> allowedRoles,
            Integer rateLimit,
            Integer rateWindowSeconds,
            Boolean idempotentPost,
            Boolean stripPrefix,
            Boolean requireUserJwt,
            Map<String, String> requestHeaders,
            Boolean enabled,
            List<String> allowedOrigins,
            String oidcIssuer,
            String oidcAudience,
            List<String> requiredScopes
    ) {
    }

    public record RouteResponse(
            String id,
            String clientId,
            String name,
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
            boolean requireUserJwt,
            Map<String, String> requestHeaders,
            boolean enabled,
            List<String> allowedOrigins,
            String oidcIssuer,
            String oidcAudience,
            List<String> requiredScopes,
            String relativePath
    ) {
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
            boolean requireUserJwt,
            Map<String, String> requestHeaders,
            boolean enabled,
            List<String> allowedOrigins,
            String oidcIssuer,
            String oidcAudience,
            List<String> requiredScopes
    ) {
    }

    public record AuditResponse(String id, String action, String path, String detail, Instant createdAt) {
    }
}
